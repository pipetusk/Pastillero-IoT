package com.example.pastillero

// Conexión con Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
// Herramienta de Java para encriptar
import java.security.MessageDigest
// Para obtener la hora del celular
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FirebaseHelper {
    // 1. Conexión con el Firebase
    private val db = FirebaseDatabase.getInstance().reference

    // 2. Creación automática de credenciales (sin la consola de Firebase)
    // El init se ejecuta apenas inicia la clase
    init {
        db.child("usuarios").child("admin").get().addOnSuccessListener { snapshot ->
            // Si NO existe el snapshot (los datos obtenidos en la setnencia anterior)
            if (!snapshot.exists()) {
                val passAdmin = hashSHA256("1234")
                val passUser = hashSHA256("1111")

                db.child("usuarios").child("admin").setValue(mapOf("clave_encriptada" to passAdmin, "rol" to "admin"))
                db.child("usuarios").child("paciente").setValue(mapOf("clave_encriptada" to passUser, "rol" to "usuario"))
            }
        }
    }

    // 3. Encriptación en SHA-256 para las credenciales
    private fun hashSHA256(texto: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(texto.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // 4. Login
    // Usé un callback pq con un return no funcionaría al consultar datos de Internet (es instantáneo)
    fun login(user: String, pass: String, respuestaFirebase: (String) -> Unit) {
        // Encripta de una lo que se escribió
        val passHash = hashSHA256(pass)

        //Viaja a Firebase para obtener datos y buscar el usuario
        db.child("usuarios").child(user).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                // Se extrae la contraseña almacenada y el rol
                val dbPass = snapshot.child("clave_encriptada").value.toString()
                val dbRol = snapshot.child("rol").value.toString()

                // Si la contraseña ingresada coincide con la almacenada, devuelve el rol
                // Si no coincide, no devuelve nada mediante el callback
                if (passHash == dbPass) {
                    respuestaFirebase(dbRol)
                } else {
                    respuestaFirebase("")
                }
            } else {
                respuestaFirebase("")
            }
        // En caso de error
        }.addOnFailureListener {
            respuestaFirebase("")
        }
    }

    // 5. Guardar los logs en firebase
    fun guardarLog(accion: String) {
        // Fecha y hora del celular
        // Este es el molde para que se vea bonito
        val formato = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        // Aqui se obtiene la hora
        val fechaActual = formato.format(Date())

        // Empaqueta los datos
        val mapaDatos = mapOf(
            "accion" to accion,
            "fecha" to fechaActual
        )
        // Mete los datos empaquetados de "mapaDatos" en el historial de la base de datos
        // Por si acaso profe, usé push y no setValue pq si no, sobreescribiría los datos
        // La idea es que se generen varios registros, no que se sobreescriba uno solop
        db.child("historial").push().setValue(mapaDatos)
    }

    // 6. Logs en tiempo real
    // En vez de recibir un dato concreto, recibe una lista de registros
    fun escucharLogs(actualizacionFirebase: (List<String>) -> Unit) {
        // Usé el addValueEventListener pq el get (como en el login) solo lee una vez y yap, este no
        db.child("historial").addValueEventListener(object : ValueEventListener {
            // Con el override "sobreescribo" en las funciones de Firebase
            override fun onDataChange(snapshot: DataSnapshot) {
                val lista = mutableListOf<String>()

                // El children hace como una colección con los datos, carpetas en la base de datos
                for (item in snapshot.children) {
                    val act = item.child("accion").value.toString()
                    val time = item.child("fecha").value.toString()
                    lista.add("$time - $act")
                }

                // El reversed es para que el nuevo siempre quede al inicio
                actualizacionFirebase(lista.reversed())
            }

            // EN CASO DE CUALQUIER COSAAA
            override fun onCancelled(error: DatabaseError) {
                println("Error de Firebase: ${error.message}")
            }
        })
    }
}