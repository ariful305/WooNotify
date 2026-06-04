// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
}

// Programmatically set environmental variables for signing configurations
try {
  val envs = mapOf(
    "STORE_PASSWORD" to "woonotifypass",
    "KEY_PASSWORD" to "woonotifypass",
    "KEYSTORE_PATH" to "${rootDir}/my-upload-key.jks"
  )
  
  try {
    val processEnv = Class.forName("java.lang.ProcessEnvironment")
    val variableClass = Class.forName("java.lang.ProcessEnvironment\$Variable")
    val valueClass = Class.forName("java.lang.ProcessEnvironment\$Value")
    
    val variableValueOf = variableClass.getDeclaredMethod("valueOf", String::class.java).apply { isAccessible = true }
    val valueValueOf = valueClass.getDeclaredMethod("valueOf", String::class.java).apply { isAccessible = true }
    
    val theEnvField = processEnv.getDeclaredField("theEnvironment").apply { isAccessible = true }
    val currentEnv = theEnvField.get(null) as MutableMap<Any, Any>
    
    envs.forEach { (k, v) ->
      val wrappedKey = variableValueOf.invoke(null, k)
      val wrappedValue = valueValueOf.invoke(null, v)
      currentEnv[wrappedKey] = wrappedValue
    }
    
    val theCaseInsensitiveEnvField = processEnv.getDeclaredField("theCaseInsensitiveEnvironment").apply { isAccessible = true }
    val currentCaseInsensitiveEnv = theCaseInsensitiveEnvField.get(null) as MutableMap<Any, Any>
    
    envs.forEach { (k, v) ->
      val wrappedKey = variableValueOf.invoke(null, k)
      val wrappedValue = valueValueOf.invoke(null, v)
      currentCaseInsensitiveEnv[wrappedKey] = wrappedValue
    }
  } catch (e: Exception) {
    // Non-Unix or other Java version fallback
    try {
      val unmodifiableMapClass = Class.forName("java.util.Collections\$UnmodifiableMap")
      val mField = unmodifiableMapClass.getDeclaredField("m").apply { isAccessible = true }
      val currentEnv = System.getenv()
      val envMap = mField.get(currentEnv) as MutableMap<String, String>
      envMap.putAll(envs)
    } catch (e2: Exception) {
      // Silent ignore
    }
  }
} catch (e: Exception) {
  project.logger.warn("Could not set environment variables via reflection: ${e.message}")
}

tasks.register<Exec>("generateKeystore") {
  commandLine(
    "keytool", "-genkeypair", "-v",
    "-keystore", "${rootDir}/my-upload-key.jks",
    "-alias", "upload",
    "-keyalg", "RSA",
    "-keysize", "2048",
    "-validity", "10000",
    "-storepass", "woonotifypass",
    "-keypass", "woonotifypass",
    "-dname", "CN=WooNotify, O=WooNotify, C=US",
    "-storetype", "PKCS12"
  )
}
