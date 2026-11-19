package com.example.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun isQuotaExceededError(throwable: Throwable?): Boolean {
    if (throwable == null) return false
    val chain = generateSequence(throwable) { it.cause }
    return chain.any { err ->
        (err is FirebaseFirestoreException && err.code == FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED) ||
            (err.message?.contains("quota", ignoreCase = true) == true) ||
            (err.message?.contains("RESOURCE_EXHAUSTED", ignoreCase = true) == true)
    }
}

fun toBengaliFirestoreMessage(throwable: Throwable): String {
    if (isQuotaExceededError(throwable)) {
        return "সতর্কবার্তা: Firebase Spark (ফ্রি) প্ল্যানের দৈনিক কোটা শেষ হয়ে গেছে। আগামীকাল কোটা রিসেট হবে। কোনো Paid Billing চালু করা হয়নি।"
    }
    val firestoreEx = generateSequence(throwable) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    return when (firestoreEx?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "অনুমতি নেই (Permission Denied): আপনার অ্যাকাউন্টে এই কাজ করার অনুমতি নেই।"
        FirebaseFirestoreException.Code.UNAVAILABLE ->
            "ইন্টারনেট বা সার্ভার সংযোগ পাওয়া যাচ্ছে না। অনুগ্রহ করে সংযোগ পরীক্ষা করুন।"
        else -> throwable.localizedMessage ?: "অপারেশন সম্পন্ন করা যায়নি।"
    }
}

fun handleFirestoreError(exception: Throwable, operationType: OperationType, path: String?): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val currentUser = try {
        FirebaseAuth.getInstance().currentUser
    } catch (_: Exception) {
        null
    }

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}
