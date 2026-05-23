package com.pralayakaveri.medisave.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pralayakaveri.medisave.data.AppDatabase
import com.pralayakaveri.medisave.data.MedicineEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.InputStreamReader

data class MedicineJson(
    val brand_name: String,
    val salt_composition: String,
    val price: Double,
    val manufacturer: String,
    val pack_size: Int? = null
)

class GenericViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val dao = db.medicineDao()

    private val _searchResults = MutableStateFlow<List<MedicineEntity>>(emptyList())
    val searchResults: StateFlow<List<MedicineEntity>> = _searchResults

    private val _alternatives = MutableStateFlow<List<MedicineEntity>>(emptyList())
    val alternatives: StateFlow<List<MedicineEntity>> = _alternatives

    private val _selectedMedicine = MutableStateFlow<MedicineEntity?>(null)
    val selectedMedicine: StateFlow<MedicineEntity?> = _selectedMedicine

    init {
        viewModelScope.launch(Dispatchers.IO) {
            checkAndLoadData(application)
        }
    }

    private suspend fun checkAndLoadData(context: Context) {
        if (dao.getCount() == 0) {
            try {
                val inputStream = context.assets.open("medicines_dataset.json")
                val reader = InputStreamReader(inputStream)
                val type = object : TypeToken<List<MedicineJson>>() {}.type
                val items: List<MedicineJson> = Gson().fromJson(reader, type)
                
                val entities = items.map {
                    val strength = extractStrength(it.brand_name, it.salt_composition)
                    MedicineEntity(
                        brandName = it.brand_name,
                        saltComposition = it.salt_composition,
                        normalizedSalt = normalizeSalt(it.salt_composition),
                        price = it.price,
                        manufacturer = it.manufacturer,
                        strength = strength,
                        packSize = it.pack_size
                    )
                }
                
                dao.insertAll(entities)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun normalizeSalt(rawSalt: String): String {
        return rawSalt.lowercase()
            .replace(Regex("([0-9]+\\.?[0-9]*\\s*(mg|ml|mcg|g))"), "")
            .trim()
    }

    private fun extractStrength(brandName: String, saltComposition: String): String {
        val combined = "$saltComposition $brandName"
        
        // 1. Explicit metric matching (e.g. 500mg, 0.5g, 10ml)
        val explicitRegex = Regex("([0-9]+(?:\\.[0-9]+)?\\s*(?:mg|ml|mcg|g))", RegexOption.IGNORE_CASE)
        val explicitMatch = explicitRegex.find(combined)
        
        if (explicitMatch != null) {
            var strength = explicitMatch.value.lowercase().replace(" ", "")
            // Normalize "0.5g" strictly into "500mg" parity
            if (strength.endsWith("g") && !strength.endsWith("mg") && !strength.endsWith("mcg")) {
                val numPart = strength.removeSuffix("g").toDoubleOrNull()
                if (numPart != null) {
                    return "${(numPart * 1000).toInt()}mg"
                }
            }
            return strength
        }
        
        // 2. Standalone number fallback (e.g. "Dolo 650", "Atorva 10")
        val bareNumberRegex = Regex("(?<=\\s|^|-)([0-9]{1,4})(?:\\s|$)", RegexOption.IGNORE_CASE)
        val bareMatch = bareNumberRegex.find(brandName) 
        if (bareMatch != null) {
            return "${bareMatch.groupValues[1]}mg" // safely default to mg
        }
        
        return ""
    }

    fun searchBrand(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _searchResults.value = dao.searchBrand(query)
        }
    }

    fun selectMedicine(medicine: MedicineEntity?) {
        _selectedMedicine.value = medicine
        // SAFETY ENFORCEMENT: Exclude from alternative matching if strength is ambiguous or missing
        if (medicine != null && medicine.strength.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                dao.getAlternativesBySalt(medicine.normalizedSalt, medicine.strength).collect { alts ->
                    _alternatives.value = alts
                }
            }
        } else {
            _alternatives.value = emptyList()
        }
    }
}
