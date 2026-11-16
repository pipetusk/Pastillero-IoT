package com.example.pastillero

// Para el Bluetooth, estado de la app, pantalla y Jetpack Compose
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
// Contenedores y así, columnas lazy, componentes, tamaño de la app
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
// Estética
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
// Memoria de la pantalla
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
// Botones, ocultar teclado en login, forma del teclado (teclado numerico) y contraseña
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
// Viaje entre pantallas
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // El lateinit es para que no crashee la app, así tarda un poquito
    private lateinit var bluetoothHelper: ControladorBluetooth
    private lateinit var firebaseHelper: FirebaseHelper

    // El Bundle sirve para no perder la info al minimizar, girar la pantalla, etc.
    // Puede o no ser nulo, como vimos en clases
    override fun onCreate(instanciaGuardada: Bundle?) {
        // El super permite que el onCreate de Android prepare todito
        super.onCreate(instanciaGuardada)

        // Llenamos esas variables vacías con lo necesario
        firebaseHelper = FirebaseHelper()
        val gestorBluetooth = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothHelper = ControladorBluetooth(gestorBluetooth.adapter)

        // Lienzo
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Navegacion(bluetoothHelper, firebaseHelper)
                }
            }
        }
    }
}

// --- NAVEGACIÓN ---
@Composable
// Recibe todop lo necesario
fun Navegacion(bluetoothHelper: ControladorBluetooth, firebaseHelper: FirebaseHelper) {
    val controlNavegacion = rememberNavController()

    // AQUI el sistema se prepara e indica que la navegacion comienza en el login
    NavHost(navController = controlNavegacion, startDestination = "login") {
        // Se definen las rutas
        composable("login") { LoginPantalla(controlNavegacion, firebaseHelper) }

        // Recibe el rol como parámetro (admin o usuario), es decir puede variar
        // El backStackEntry (o NavBackStackEntry) es para saber donde está exactamente
        composable("main/{rol}") { backStackEntry ->
            val rolUsuario = backStackEntry.arguments?.getString("rol") ?: "usuario" // Aqui le asigno el usuario en caso de que falle o es nulo
            MainScreen(controlNavegacion, bluetoothHelper, firebaseHelper, rolUsuario)
        }
    }
}

// --- Pantalla 1: Login ---
@Composable
fun LoginPantalla(navController: NavController, firebaseHelper: FirebaseHelper) {
    // Aquí apliqué el "by" para no usar el ".value" tdo el rato, por comodidad (y recomendación)
    var userText by remember { mutableStateOf("") }
    var passText by remember { mutableStateOf("") }
    var intentos by remember { mutableStateOf(0) }
    var isBlocked by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // Para el teclado
    val gestorTeclado = LocalFocusManager.current

    // EL CONTENIDO VISUAL
    Column(
        modifier = Modifier.padding(20.dp).fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Pastillero IoT", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(30.dp))

        OutlinedTextField(
            value = userText,
            onValueChange = { userText = it },
            label = { Text("Usuario") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isBlocked && !isLoading, // Esto es una de las medidas de seguridad que apliqué
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(
                onNext = { gestorTeclado.moveFocus(androidx.compose.ui.focus.FocusDirection.Next) }
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = passText,
            onValueChange = { passText = it },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(), // Puntitos en la contraseña
            modifier = Modifier.fillMaxWidth(),
            enabled = !isBlocked && !isLoading,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { gestorTeclado.clearFocus() }
            )
        )

        if (errorMsg.isNotEmpty()) {
            Text(errorMsg, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))

        // El botón de "entrar", tiene mucha seguridad para cumplir con los requerimientos
        Button(
            onClick = {
                gestorTeclado.clearFocus()
                isLoading = true
                errorMsg = "Conectando..."

                // Aqui se conecta con el archivo de FirebaseHelper, recibiendo el usuario y contraseña
                firebaseHelper.login(userText, passText) { rol ->
                    isLoading = false

                    // Si el rol (con ayuda del callback) NO está vacío
                    if (rol.isNotEmpty()) {
                        firebaseHelper.guardarLog("Login exitoso: $userText")
                        navController.navigate("main/$rol") {
                            popUpTo("login") { inclusive = true } // Mata el login para no volver con "Atrás"
                        }
                    } else {
                        intentos++
                        firebaseHelper.guardarLog("Login fallido ($intentos/3): $userText")
                        if (intentos >= 3) {
                            isBlocked = true
                            errorMsg = "Sistema bloqueado (máx intentos)."
                        } else {
                            errorMsg = "Credenciales incorrectas. Intentos: $intentos/3"
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isBlocked && !isLoading // Aquí se activa el bloqueo
        ) {
            Text(if (isLoading) "Verificando" else "Entrar") // El isLoading ayuda a que no se aprete de nuevo
        }
    }
}

// --- Pantalla 2: Contenedor con navbar (abajo)
@Composable
fun MainScreen(
    navController: NavController,
    bluetoothHelper: ControladorBluetooth,
    firebaseHelper: FirebaseHelper,
    rol: String
) {
    // Esta variable cambia según el número que se asigna al presionar los botones mediante OnClick
    var tabSeleccionada by remember { mutableStateOf(0) }

    // Usé scaffold por recomendación, para aplicar el Material Design
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                    label = { Text("Control") },
                    selected = tabSeleccionada == 0,
                    onClick = { tabSeleccionada = 0 }
                )

                // Solo muestra el historial si es el admin
                if (rol == "admin") {
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.List, contentDescription = "Logs") },
                        label = { Text("Historial") },
                        selected = tabSeleccionada == 1,
                        onClick = { tabSeleccionada = 1 }
                    )
                }

                NavigationBarItem(
                    icon = { Icon(Icons.Filled.ExitToApp, contentDescription = "Salir") },
                    label = { Text("Salir") },
                    selected = false,
                    onClick = {
                        firebaseHelper.guardarLog("Sesión cerrada")
                        navController.navigate("login") { popUpTo(0) } // Así no puede volver luego de cerrar sesión
                    }
                )
            }
        }
        // Esto es necesario con el scaffold, obligatoriamente.
        // Sirve para adaptar el contenido al tamaño del navbar.
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (tabSeleccionada == 0) {
                ControlView(bluetoothHelper, firebaseHelper)
            } else {
                LogsView(firebaseHelper)
            }
        }
    }
}

// --- Vistas internas (CONTROL Y LOGS) ---
@Composable
fun ControlView(bluetoothHelper: ControladorBluetooth, firebaseHelper: FirebaseHelper) {
    var btMsg by remember { mutableStateOf("Esperando datos...") }
    var macAddress by remember { mutableStateOf("00:00:00:00:00:00") }
    var horasInput by remember { mutableStateOf("") }

    // Aquí declaramos el scope para las corrutinas
    // SIRVE MUCHO, así la app no se congela ni da fallos mientras esperamos el Bluetooth, por ejemplo
    val coroutineScope = rememberCoroutineScope()

    // Esto es para no abrir conexiones tdo el rato
    // El unit indica que solo se ejecuta esa vez y ya
    LaunchedEffect(Unit) {
        bluetoothHelper.escucharArduino { dato -> btMsg = dato }
    }

    Column(modifier = Modifier.padding(20.dp)) {
        Text("Panel de Control", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = macAddress,
            onValueChange = { macAddress = it },
            label = { Text("MAC Address del Arduino") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                // La corrutina manda la conexión al fondo para que no se pegue la app
                coroutineScope.launch(Dispatchers.IO) {
                    bluetoothHelper.conectarArduino(macAddress)
                }
            },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("Conectar Bluetooth")
        }

        Spacer(modifier = Modifier.height(15.dp))
        Text("Status Arduino:", style = MaterialTheme.typography.titleMedium)
        // Aquí el botón de arriba cambia de color de manera reactiva
        Text(btMsg, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)

        Spacer(modifier = Modifier.height(15.dp))

        // Aquí indico que el teclado sea numérico y protección contra letras u otros carácteres
        OutlinedTextField(
            value = horasInput,
            onValueChange = { if (it.all { char -> char.isDigit() }) horasInput = it },
            label = { Text("Intervalo en horas (Ej: 4)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )

        Button(
            onClick = {
                // Si el input de horas NO está vacío (el que se ingresó antes)
                if (horasInput.isNotEmpty()) {
                    // Tira el número por Bluetooth
                    bluetoothHelper.mandarInstruccion(horasInput)
                    firebaseHelper.guardarLog("Frecuencia actualizada: cada $horasInput horas")
                    horasInput = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Enviar Horario")
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                // Por ahora, el Arduino está configurado para que con el comando "P", la alarma se apague
                // Por lo mismo se indicó lo mismo en este caso
                bluetoothHelper.mandarInstruccion("P")
                firebaseHelper.guardarLog("Paciente confirmó la toma de medicamentos")
            },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
        ) {
            Text("CONFIRMAR TOMA")
        }
    }
}

@Composable
fun LogsView(firebaseHelper: FirebaseHelper) {
    // Se guarda una lista completa para el historial
    var listaLogs by remember { mutableStateOf(listOf<String>()) }

    // Para que se comunique con el firebase solo una vez
    LaunchedEffect(Unit) {
        // Aquí se llama la función donde se guardan los logs en la lista y toda la cosa
        firebaseHelper.escucharLogs { nuevosLogs ->
            listaLogs = nuevosLogs
        }
    }

    Column(modifier = Modifier.padding(20.dp)) {
        Text("Historial del Sistema", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(10.dp))

        // Aquí usé el LazyColumn para priorizar el rendimiento, así al scrollear, se destruyen los registros que no se ven
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // Parecido a un for para que recorra la lista de registros y los vaya dibujando como cartas
            items(listaLogs) { log ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(log, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
