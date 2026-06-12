const { onDocumentCreated, onDocumentUpdated, onDocumentWritten } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { setGlobalOptions } = require("firebase-functions/v2");
const admin = require("firebase-admin");
const { DateTime } = require("luxon");

if (admin.apps.length === 0) {
    admin.initializeApp();
}

// Set global options for all functions (Region: Mumbai)
setGlobalOptions({ region: "asia-south1" });

const { logger } = require("firebase-functions");

/**
 * CONFIGURATION & FEATURE FLAGS
 */
const LIVE_SEND = true; // SHADOW MODE: Set to true for live push delivery

/**
 * HELPER: Deterministic DoseLog ID Generator
 * Must match Android MedicineRepository exactly: userId_medicineId_date_time
 */
function getLogId(userId, medicineId, date, time) {
    return `${userId}_${medicineId}_${date}_${time}`;
}

/**
 * Triggers a push notification whenever a new connection request is created.
 */
exports.sendConnectionRequestNotification = onDocumentCreated(
    "connections/{requestId}",
    async (event) => {
        const snapshot = event.data;
        if (!snapshot) {
            console.log("No data associated with the event");
            return;
        }

        const data = snapshot.data();
        const requestId = event.params.requestId;

        // 1. DEDUPLICATION & STATUS CHECK
        if (data.notified === true || data.status !== "pending") {
            console.log("Request already notified or not pending:", requestId);
            return;
        }

        const receiverId = data.receiverId;
        const senderId = data.senderId;

        // 2. FETCH RECEIVER'S FCM TOKENS
        const userDoc = await admin.firestore().collection("users").doc(receiverId).get();
        if (!userDoc.exists) {
            console.log("Receiver user document not found:", receiverId);
            return;
        }

        const userData = userDoc.data();
        if (userData.pushNotificationsEnabled === false) {
            console.log("Receiver has disabled push notifications. Skipping.");
            return;
        }

        const fcmTokens = userData.fcmTokens || [];

        if (fcmTokens.length === 0) {
            console.log("No FCM tokens found for user:", receiverId);
            return;
        }

        // 3. PREPARE NOTIFICATION + DATA PAYLOAD
        const message = {
            notification: {
                title: "New Connection Request",
                body: "Someone wants to connect with your MediSave account",
            },
            data: {
                type: "CONNECTION_REQUEST",
                requestId: requestId,
                senderId: senderId,
                click_action: "OPEN_CONNECTION_REQUEST",
            },
            android: {
                priority: "high",
                ttl: 60000, // 60 seconds (Stale prevention)
            },
            tokens: fcmTokens,
        };

        try {
            // 4. SEND MULTICAST
            const response = await admin.messaging().sendEachForMulticast(message);

            // 5. CLEANUP INVALID TOKENS
            const tokensToRemove = [];
            response.responses.forEach((resp, idx) => {
                if (!resp.success) {
                    const error = resp.error;
                    if (
                        error.code === "messaging/invalid-registration-token" ||
                        error.code === "messaging/registration-token-not-registered"
                    ) {
                        tokensToRemove.push(fcmTokens[idx]);
                    }
                }
            });

            if (tokensToRemove.length > 0) {
                console.log(`Removing ${tokensToRemove.length} invalid tokens for user:`, receiverId);
                await admin.firestore().collection("users").doc(receiverId).update({
                    fcmTokens: admin.firestore.FieldValue.arrayRemove(...tokensToRemove),
                });
            }

            // 6. ATOMICALLY MARK AS NOTIFIED
            return snapshot.ref.update({ notified: true });
        } catch (error) {
            console.error("Error sending FCM notification:", error);
        }
    }
);



/**
 * REACTION LAYER: Triggers push notifications on DoseLog state changes.
 * Primarily listens for PENDING -> MISSED transitions.
 */
exports.onDoseLogWritten = onDocumentWritten(
    "doseLogs/{userId}/logs/{logId}",
    async (event) => {
        const userId = event.params.userId;
        const logId = event.params.logId;
        const correlationId = `[${userId}_${logId}]`;

        logger.info(`${correlationId} TRIGGER_ENTERED: onDoseLogWritten fired.`);

        const before = event.data.before ? event.data.before.data() : null;
        const after = event.data.after ? event.data.after.data() : null;

        if (!after) {
            logger.info(`${correlationId} Skip: Document deleted.`);
            return null;
        }

        logger.info(`${correlationId} Status: ${before ? before.status : "null"} -> ${after.status}`);

        // 1. TRANSITION FILTER: Only notify on transition to MISSED
        const wasMissed = before ? before.status === "MISSED" : false;
        const isMissed = after.status === "MISSED";

        if (wasMissed || !isMissed) {
            logger.info(`${correlationId} [TRIGGER_SKIP] Not a MISSED transition (isMissed: ${isMissed}, wasMissed: ${wasMissed})`);
            return null;
        }

        // 2. FEATURE FLAG & CONFIG CHECK
        try {
            const configDoc = await admin.firestore().collection("config").doc("alerts").get();
            const configData = configDoc.exists ? configDoc.data() : null;
            const caregiverPushEnabled = configData ? configData.caregiverPushEnabled : false;

            logger.info(`${correlationId} Config: caregiverPushEnabled=${caregiverPushEnabled}, LIVE_SEND=${LIVE_SEND}`);

            if (!caregiverPushEnabled && !LIVE_SEND) {
                logger.info(`${correlationId} [METRIC] TRIGGER_SKIP: Master switch off.`);
                return null;
            }
        } catch (error) {
            logger.error(`${correlationId} Error reading config/alerts:`, error);
            // In shadow mode or if LIVE_SEND is true, we might want to proceed or fail safe.
            // For now, let's proceed only if LIVE_SEND is true.
            if (!LIVE_SEND) return null;
        }

        // 3. CAREGIVER ALERT ENABLED CHECK (on the log itself)
        if (after.caregiverAlertEnabled === false) {
            logger.info(`${correlationId} [TRIGGER_SKIP] Caregiver alerts disabled in doseLog metadata.`);
            return null;
        }

        // 4. RESOLVE RECIPIENTS & SEND
        logger.info(`${correlationId} [METRIC] ALERT_CANDIDATE: Processing missed dose payload.`);
        
        const note = {
            patientUid: userId,
            patientName: after.patientName || "Family Member",
            medicineName: after.medicineName,
            time: after.time,
            logId: logId,
            correlationId: correlationId
        };

        try {
            return await notifyCaregivers(note);
        } catch (error) {
            logger.error(`${correlationId} Unhandled error in notifyCaregivers:`, error);
            return null;
        }
    }
);

/**
 * PHASE 2 WATCHDOG: Highly scalable scheduled function.
 * Hardened with:
 * - nextCheckAt targeted query (indexed)
 * - notifiedMap idempotency guard
 * - Deterministic DoseLog creation via Transactions
 */
exports.checkMissedDoses = onSchedule({
    schedule: "* * * * *",
    timeZone: "Asia/Kolkata",
    memory: "256MiB"
}, async (event) => {
    const db = admin.firestore();
    const now = admin.firestore.Timestamp.now();
    const nowMillis = now.toMillis();
    
    const remindersSnapshot = await db.collectionGroup("medicines")
        .where("nextCheckAt", "<=", nowMillis)
        .where("nextCheckAt", ">", 0)
        .limit(500) // Reduced batch for transaction safety
        .get();
    
    logger.info(`[WATCHDOG] Wakeup: Evaluating ${remindersSnapshot.size} potential missed doses.`);
    
    for (const medDoc of remindersSnapshot.docs) {
        await db.runTransaction(async (transaction) => {
            const data = medDoc.data();
            const patientUid = medDoc.ref.parent.parent.id;
            const medicineId = medDoc.id;
            const { timezone, times, statusMap, name, repeatDays, gracePeriodMinutes = 10, caregiverAlertEnabled = true, notifiedMap = {} } = data;
            
            logger.info(`[TIMING_TELEMETRY] Watchdog Evaluation | name: ${name} | nextCheckAt: ${data.nextCheckAt} | gracePeriodMinutes: ${gracePeriodMinutes} | candidate selection reason: nextCheckAt <= nowMillis (${data.nextCheckAt} <= ${nowMillis})`);
            
            const userNow = DateTime.now().setZone(timezone || "Asia/Kolkata");
            const todayStr = userNow.toFormat("yyyy-MM-dd");
            
            let docUpdated = false;
            let updatedStatusMap = { ...statusMap };
            let updatedNotifiedMap = { ...notifiedMap };

            for (const time of times) {
                const statusKey = `${todayStr}_${time}`;
                const currentStatus = statusMap[statusKey] || "PENDING";
                
                if (currentStatus === "PENDING") {
                    const [hour, minute] = time.split(":").map(Number);
                    const doseTime = userNow.set({ hour, minute, second: 0, millisecond: 0 });
                    const diffMinutes = userNow.diff(doseTime, "minutes").minutes;
                    
                    logger.info(`[TIMING_TELEMETRY] Dose check for ${name} at ${time} | diffMinutes: ${Math.round(diffMinutes)} | gracePeriodMinutes: ${gracePeriodMinutes}`);
                    
                    if (diffMinutes >= gracePeriodMinutes) {
                        const logId = getLogId(patientUid, medicineId, todayStr, time);
                        const logRef = db.collection("doseLogs").doc(patientUid).collection("logs").doc(logId);
                        const logSnap = await transaction.get(logRef);

                        // SSOT Protection: Do not overwrite if client already synced a TAKEN status
                        if (logSnap.exists && logSnap.data().status === "TAKEN") {
                            logger.info(`[WATCHDOG] Skip: Client already synced TAKEN | ${logId}`);
                            updatedStatusMap[statusKey] = "TAKEN";
                            docUpdated = true;
                            continue;
                        }

                        logger.info(`[WATCHDOG] MISSED: ${name} | ${statusKey} | Diff: ${Math.round(diffMinutes)}m`);
                        updatedStatusMap[statusKey] = "MISSED";
                        docUpdated = true;

                        // ATOMIC DOSE LOG CREATION/UPDATE
                        transaction.set(logRef, {
                            userId: patientUid,
                            medicineId: medicineId,
                            medicineName: name,
                            date: todayStr,
                            time: time,
                            status: "MISSED",
                            timestamp: admin.firestore.FieldValue.serverTimestamp(),
                            lastUpdatedAt: admin.firestore.Timestamp.now(),
                            caregiverAlertEnabled: caregiverAlertEnabled,
                            patientName: data.patientName || "Family Member"
                        }, { merge: true });
                    }
                }
            }

            if (docUpdated) {
                const nextCheckAt = calculateNextCheckAtServer({ ...data, statusMap: updatedStatusMap }, userNow);
                transaction.update(medDoc.ref, { 
                    statusMap: updatedStatusMap,
                    nextCheckAt: nextCheckAt,
                    lastUpdated: admin.firestore.FieldValue.serverTimestamp()
                });
            }
        });
    }

    return null;
});

/**
 * Server-side helper to calculate the next check time
 */
function calculateNextCheckAtServer(medicine, userNow) {
    const { times, statusMap, repeatDays, gracePeriodMinutes = 10 } = medicine;
    const today = userNow;
    
    for (let i = 0; i <= 1; i++) {
        const date = today.plus({ days: i });
        const dateStr = date.toFormat("yyyy-MM-dd");
        const dayOfWeek = date.weekday === 7 ? 7 : date.weekday; 
        
        if (repeatDays.includes(dayOfWeek)) {
            for (const timeStr of times) {
                const statusKey = `${dateStr}_${timeStr}`;
                const status = statusMap[statusKey] || "PENDING";
                
                if (status === "PENDING") {
                    const [hour, minute] = timeStr.split(":").map(Number);
                    const checkTime = date.set({ hour, minute, second: 0, millisecond: 0 }).plus({ minutes: gracePeriodMinutes });
                    
                    if (checkTime > userNow) {
                        return checkTime.toMillis();
                    }
                }
            }
        }
    }
    return 0;
}

/**
 * CORE NOTIFICATION DISPATCHER
 * Hardened with:
 * - Transactional deduplication via notifiedTo
 * - Shadow Mode logging with metrics
 * - Data-only v1.1 Payload
 */
async function notifyCaregivers(note) {
    const db = admin.firestore();
    const { patientUid, logId, correlationId } = note;
    
    return db.runTransaction(async (transaction) => {
        const logRef = db.collection("doseLogs").doc(patientUid).collection("logs").doc(logId);
        const logSnap = await transaction.get(logRef);
        
        if (!logSnap.exists) {
            logger.error(`${correlationId} Log document missing in notifyCaregivers: ${logId}`);
            return;
        }

        const logData = logSnap.data();
        const notifiedTo = logData.notifiedTo || [];

        // 1. RESOLVE CAREGIVERS
        const [q1, q2] = await Promise.all([
            db.collection("connections").where("status", "==", "accepted").where("receiverId", "==", patientUid).get(),
            db.collection("connections").where("status", "==", "accepted").where("senderId", "==", patientUid).get()
        ]);

        const caregiverIds = new Set();
        q1.forEach(doc => caregiverIds.add(doc.data().senderId));
        q2.forEach(doc => caregiverIds.add(doc.data().receiverId));

        logger.info(`${correlationId} Resolved ${caregiverIds.size} potential caregivers: [${Array.from(caregiverIds).join(", ")}]`);

        if (caregiverIds.size === 0) {
            logger.info(`${correlationId} [METRIC] RECIPIENT_FAILURE: No caregivers found for ${patientUid}`);
            return;
        }

        // 2. DEDUPLICATE & TOKEN RESOLUTION
        const newRecipientIds = Array.from(caregiverIds).filter(cid => !notifiedTo.includes(cid));
        
        if (newRecipientIds.length === 0) {
            logger.info(`${correlationId} [METRIC] DEDUPE_SKIPPED: All caregivers already notified.`);
            return;
        }

        const tokens = [];
        for (const cid of newRecipientIds) {
            const userDoc = await db.collection("users").doc(cid).get();
            if (!userDoc.exists) {
                logger.info(`${correlationId} Skip: User doc missing for caregiver ${cid}`);
                continue;
            }

            const userData = userDoc.data();
            const pushEnabled = userData.pushNotificationsEnabled !== false;
            const familyEnabled = userData.familyAlertsEnabled !== false;
            const userTokens = userData.fcmTokens || [];

            logger.info(`${correlationId} Caregiver ${cid}: pushEnabled=${pushEnabled}, familyEnabled=${familyEnabled}, tokens=${userTokens.length}`);

            if (pushEnabled && familyEnabled && userTokens.length > 0) {
                tokens.push(...userTokens);
            }
        }

        if (tokens.length === 0) {
            logger.info(`${correlationId} [METRIC] RECIPIENT_FAILURE: Zero active tokens collected for ${newRecipientIds.length} candidate caregivers.`);
            return;
        }

        // Deduplicate tokens (in case a user is logged into multiple devices with same token, or shared tokens)
        const uniqueTokens = [...new Set(tokens)];
        logger.info(`${correlationId} Pre-send Token Summary: Total collected=${tokens.length}, Unique=${uniqueTokens.length} | Recipients=${newRecipientIds.length}`);

        // 3. PREPARE v1.1 PAYLOAD (Data-only)
        const message = {
            data: {
                type: "CAREGIVER_ALERT",
                event: "MISSED_DOSE",
                eventVersion: "1.1",
                patientId: patientUid,
                patientName: note.patientName,
                logId: logId,
                medicineName: note.medicineName,
                scheduledTime: note.time,
                targetScreen: "member_detail",
                timestamp: String(Date.now()),
                correlationId: correlationId
            },
            tokens: uniqueTokens
        };

        // 4. SHADOW MODE VS LIVE SEND
        if (!LIVE_SEND) {
            logger.info(`${correlationId} [SHADOW] Would send MISSED_DOSE to ${tokens.length} devices.`);
            transaction.update(logRef, { notifiedTo: admin.firestore.FieldValue.arrayUnion(...newRecipientIds) });
            return;
        }

        try {
            await admin.messaging().sendEachForMulticast(message);
            transaction.update(logRef, { notifiedTo: admin.firestore.FieldValue.arrayUnion(...newRecipientIds) });
            logger.info(`${correlationId} [METRIC] ALERT_SENT: Success for ${tokens.length} devices across ${newRecipientIds.length} caregivers.`);
        } catch (error) {
            logger.error(`${correlationId} [NOTIFY] Error sending FCM:`, error);
        }
    });
}

/**
 * Triggers a push notification to caregivers when a request is accepted.
 */
exports.notifyConnectionAccepted = onDocumentUpdated(
    "connections/{requestId}",
    async (event) => {
        const before = event.data.before.data();
        const after = event.data.after.data();

        if (before.status !== "accepted" && after.status === "accepted") {
            const caregiverId = after.senderId;
            const patientName = after.receiverName || "A family member";
            const relation = after.relation || "Family Member";

            try {
                const caregiverDoc = await admin.firestore().collection("users").doc(caregiverId).get();
                if (!caregiverDoc.exists) return;

                const caregiverData = caregiverDoc.data();
                if (caregiverData.pushNotificationsEnabled === false) return;

                const fcmTokens = caregiverData.fcmTokens || [];
                if (fcmTokens.length === 0) return;

                const message = {
                    data: {
                        type: "CONNECTION_ACCEPTED",
                        title: `${patientName} (${relation}) is now connected`,
                        body: "You'll be notified if anything needs attention",
                        requestId: event.params.requestId,
                        receiverId: after.receiverId,
                        relation: relation,
                        screen: "connection_success"
                    },
                    tokens: fcmTokens,
                };

                await admin.messaging().sendEachForMulticast(message);
            } catch (error) {
                console.error("Error in notifyConnectionAccepted:", error);
            }
        }
    }
);

