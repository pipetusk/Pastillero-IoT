package com.example.pastillero

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Conexión con el Bluetooth del dispositivo
class ControladorBluetooth(private val adaptadorBt: BluetoothAdapter?) {


    // Protección mediante private, y pueden ser nulos
    private var conexionActiva: BluetoothSocket? = null
    private var canalEnvio: OutputStream? = null
    private var canalRecepcion: InputStream? = null

    // UUID universal para módulos seriales como el HC-05
    private val CODIGO_SERIAL: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @SuppressLint("MissingPermission") // Para evitar errores o mensajes de peligro
    // Al ser suspend, esta puede pausar su funcionamiento para reaunudarla desde una corrutina
    // Con el Boolean te "comprometes" a entregar un booleano, como se ve en este caso al final del try y catch.
    suspend fun conectarArduino(direccionMac: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Se busca el dispositivo (el módulo bluetooth del arduino)
            val dispositivo: BluetoothDevice? = adaptadorBt?.getRemoteDevice(direccionMac)
            conexionActiva = dispositivo?.createRfcommSocketToServiceRecord(CODIGO_SERIAL)
            // Intenta la conexión
            conexionActiva?.connect()

            //Si funcionó, aqui se crea el tunel para enviar y recibir
            canalEnvio = conexionActiva?.outputStream
            canalRecepcion = conexionActiva?.inputStream
            true
        } catch (error: Exception) {
            // Cierra los intentos fallidos y la conexión como tal, aunque haya quedado a medias
            conexionActiva?.close()
            false
        }
    }

    // Convierte el texto ('1', '2', 'P') en bytes y lo envía
    fun mandarInstruccion(comando: String) {
        try {
            // El hardware bluetooth solo entiende bytes, así que tocó convertirlos
            canalEnvio?.write(comando.toByteArray())
        } catch (error: Exception) {
            println("Falló el envío de datos")
        }
    }

    // Lee constantemente lo que imprime el Arduino
    fun escucharArduino(alRecibirDato: (String) -> Unit) {
        val memoriaTemporal = ByteArray(1024)
        // El proceso quedó encerrado!!
        Thread {
            while (true) {
                try {
                    val cantidadBytes = canalRecepcion?.read(memoriaTemporal) ?: 0
                    if (cantidadBytes > 0) {
                        val textoRecibido = String(memoriaTemporal, 0, cantidadBytes)
                        alRecibirDato(textoRecibido)
                    }
                } catch (error: Exception) {
                    break // Se cortó la señal
                }
            }
        }.start()
    }
}