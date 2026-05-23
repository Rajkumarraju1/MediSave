const { DateTime } = require("luxon");

// --- LOGGER MOCK ---
const logger = {
    info: (msg) => console.log(`[INFO] ${msg}`),
    error: (msg, err) => console.log(`[ERROR] ${msg}`, err || "")
};

// --- MOCK STATE ---
const dbState = {
    doseLogs: {}, 
    connections: [
        { senderId: "caregiver1", receiverId: "patient1", status: "accepted" }
    ],
    users: {
        "caregiver1": { fcmTokens: ["token_c1"], pushNotificationsEnabled: true, familyAlertsEnabled: true },
        "patient1": { name: "John Patient" }
    },
    config: {
        "alerts": { caregiverPushEnabled: true }
    }
};

// --- MOCK FIREBASE ADMIN ---
const mockAdmin = {
    firestore: () => ({
        collection: (name) => ({
            doc: (id) => ({
                get: async () => ({
                    exists: !!dbState[name]?.[id],
                    data: () => dbState[name]?.[id],
                    id: id,
                    ref: { path: `${name}/${id}` }
                }),
                collection: (sub) => ({
                    document: (subId) => ({
                        get: async () => ({
                            exists: !!dbState.doseLogs[subId],
                            data: () => dbState.doseLogs[subId],
                            id: subId,
                            path: `doseLogs/${id}/logs/${subId}`
                        })
                    })
                })
            }),
            where: (field, op, val) => ({
                where: (f2, o2, v2) => ({
                    get: async () => {
                        const results = (dbState[name] || []).filter(item => 
                            item[field] === val && item[f2] === v2
                        );
                        return {
                            forEach: (cb) => results.forEach(r => cb({ data: () => r })),
                            size: results.length
                        };
                    }
                })
            })
        }),
        runTransaction: async (cb) => {
            const transaction = {
                get: async (ref) => {
                    const parts = ref.path.split("/");
                    if (parts[0] === "doseLogs") return { exists: !!dbState.doseLogs[parts[3]], data: () => dbState.doseLogs[parts[3]] };
                    return { exists: false };
                },
                update: (ref, data) => {
                    const parts = ref.path.split("/");
                    dbState.doseLogs[parts[3]] = { ...dbState.doseLogs[parts[3]], ...data };
                }
            };
            return cb(transaction);
        },
        FieldValue: {
            arrayUnion: (...args) => args,
            serverTimestamp: () => Date.now()
        }
    }),
    messaging: () => ({
        sendEachForMulticast: async (msg) => {
            return { responses: [{ success: true }] };
        }
    })
};

const admin = mockAdmin;
const LIVE_SEND = true;

// Helpers
function getLogId(userId, medicineId, date, time) {
    return `${userId}_${medicineId}_${date}_${time}`;
}

async function notifyCaregivers(note) {
    const db = admin.firestore();
    const { patientUid, logId, correlationId } = note;
    
    return db.runTransaction(async (transaction) => {
        const logRef = { path: `doseLogs/${patientUid}/logs/${logId}` };
        const logSnap = await transaction.get(logRef);
        if (!logSnap.exists) return;

        const caregiverIds = ["caregiver1"];
        const tokens = ["token_c1"];

        const message = { data: { type: "CAREGIVER_ALERT" }, tokens: tokens };
        
        await admin.messaging().sendEachForMulticast(message);
        logger.info(`${correlationId} [METRIC] ALERT_SENT: Success for ${tokens.length} devices.`);
    });
}

// TRIGGER UNDER TEST
async function onDoseLogWrittenSim(userId, logId, beforeData, afterData) {
    const correlationId = `[${userId}_${logId}]`;
    logger.info(`${correlationId} TRIGGER_ENTERED: onDoseLogWritten fired.`);

    const before = beforeData;
    const after = afterData;

    if (!after) {
        logger.info(`${correlationId} Skip: Document deleted.`);
        return null;
    }

    logger.info(`${correlationId} Status: ${before ? before.status : "null"} -> ${after.status}`);

    const wasMissed = before ? before.status === "MISSED" : false;
    const isMissed = after.status === "MISSED";

    if (wasMissed || !isMissed) {
        logger.info(`${correlationId} [TRIGGER_SKIP] Not a MISSED transition (isMissed: ${isMissed}, wasMissed: ${wasMissed})`);
        return null;
    }

    const configDoc = await admin.firestore().collection("config").doc("alerts").get();
    const configData = configDoc.exists ? configDoc.data() : null;
    const caregiverPushEnabled = configData ? configData.caregiverPushEnabled : false;

    logger.info(`${correlationId} Config: caregiverPushEnabled=${caregiverPushEnabled}, LIVE_SEND=${LIVE_SEND}`);

    if (!caregiverPushEnabled && !LIVE_SEND) {
        logger.info(`${correlationId} [METRIC] TRIGGER_SKIP: Master switch off.`);
        return null;
    }

    if (after.caregiverAlertEnabled === false) {
        logger.info(`${correlationId} [TRIGGER_SKIP] Caregiver alerts disabled in doseLog metadata.`);
        return null;
    }

    logger.info(`${correlationId} [METRIC] ALERT_CANDIDATE: Processing missed dose payload.`);
    
    const note = {
        patientUid: userId,
        patientName: after.patientName || "Family Member",
        medicineName: after.medicineName,
        time: after.time,
        logId: logId,
        correlationId: correlationId
    };

    return await notifyCaregivers(note);
}

// RUN SIMULATION
async function run() {
    const userId = "patient1";
    const logId = "log_missed_999";
    
    console.log("--- SIMULATING FRESH MISSED DOSE ---");
    dbState.doseLogs[logId] = { status: "MISSED", medicineName: "Vitamin D", time: "09:00", patientName: "John" };
    
    await onDoseLogWrittenSim(userId, logId, null, dbState.doseLogs[logId]);
}

run();
