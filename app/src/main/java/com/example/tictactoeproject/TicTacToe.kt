package com.tictac.id1

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.asStateFlow
import com.tictac.id1.R
import kotlin.text.clear
import androidx.compose.material.*
import androidx.compose.material.icons.filled.*

@Composable
fun TicTacToe() {
    val navController = rememberNavController()
    val model = TicTacToeViewModel()
    model.loadPlayers()

    NavHost(navController = navController, startDestination = "player") {
        composable("player") { NewPlayerScreen(navController, model) }
        composable("lobby") { LobbyScreen(navController, model) }
        composable("game/{gameId}") { backStackEntry ->
            val gameId = backStackEntry.arguments?.getString("gameId")
            GameScreen(navController, model, gameId)
        }
    }
}

@Composable
fun NewPlayerScreen(navController: NavController, model: TicTacToeViewModel) {

    // saving the value on some local server, so once value is in its hard to remove for some reason, saving the value on the local phone
    val sharedPreferences = LocalContext.current
        .getSharedPreferences("TicTacToePref", Context.MODE_PRIVATE)

    //remove all data from shared preferences
    val editor = sharedPreferences.edit()
    editor.clear() // Remove all data from Shared Preferences
    editor.apply()



    // Check for playerId in SharedPreferences and navigate to lobby if found
    LaunchedEffect(Unit) {
        model.localPlayerId.value = sharedPreferences.getString("playerId", null)
        if (model.localPlayerId.value != null) {
            navController.navigate("lobby")
        }
    }

    // If playerId is not found in SharedPreferences, display UI for creating a new player
    if (model.localPlayerId.value == null) {
        var playerName by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Welcome to TicTacToe!")

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = playerName,
                onValueChange = { playerName = it },
                label = { Text("Enter your name") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (playerName.isNotBlank()) {
                        val newPlayer = Player(name = playerName)
                        //add the new player in firebase
                        model.db.collection("players")
                            .add(newPlayer)
                            .addOnSuccessListener { documentRef ->
                                val newPlayerId = documentRef.id
                                // store the playerId in SharedPreferences
                                sharedPreferences.edit().putString("playerId", newPlayerId).apply()

                                model.localPlayerId.value = newPlayerId
                                // now navigating to lobby
                                navController.navigate("lobby")


                            }
                            .addOnFailureListener { error ->
                                Log.e("Error", "Error creating player: ${error.message}")
                            }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Create Player")
            }
        }
    } else {
        Text("Laddar")
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyScreen(navController: NavController, model: TicTacToeViewModel) {
    val players by model.playerMap.asStateFlow().collectAsStateWithLifecycle()
    val games by model.gameMap.asStateFlow().collectAsStateWithLifecycle()

    LaunchedEffect(games) {
        games.forEach { (gameId, game) ->
            // Check if the game involves the current user and has transitioned to an active state
            if ((game.player1Id == model.localPlayerId.value || game.player2Id == model.localPlayerId.value) &&
                (game.gameState == "player1_turn" || game.gameState == "player2_turn")) {
                // Navigate to the GameScreen with the gameId
                navController.navigate("game/${gameId}")
            }
        }
    }

    var playerName = "Unknown?"
    players[model.localPlayerId.value]?.let {
        playerName = it.name
    }

    var showLeaderboard by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TicTacToe - $playerName") },
                actions = {
                    IconButton(onClick = {
                        showLeaderboard = true }) {
                        Icon(Icons.Filled.Leaderboard, contentDescription = "Leaderboard")
                    }
                }
            )
        }
    ) { innerPadding ->

        if (showLeaderboard) {
            Dialog(
                onDismissRequest = { showLeaderboard = false }
            ) {
                // Leaderboard content (LazyColumn with player statistics)
            }
        }


        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            // Find the player who sent the invite
            val invitingPlayerId = games.values.firstOrNull {
                it.gameState == "invite" && it.player1Id != model.localPlayerId.value && it.player2Id == model.localPlayerId.value
            }?.player1Id

            // Reorder the players list
            val reorderedPlayers = players.entries.sortedByDescending { (playerId, _) ->
                playerId == invitingPlayerId
            }

            items(reorderedPlayers) { (documentId, player) ->
                if (documentId != model.localPlayerId.value) { // Don't show yourself
                    ListItem(
                        headlineContent = {
                            Text("Player Name: ${player.name}")
                        },
                        trailingContent = {
                            var hasGame = false
                            games.forEach { (gameId, game) ->
                                if (game.player1Id == model.localPlayerId.value && game.gameState == "invite") {
                                    if (game.player2Id == documentId) { // Only show for the challenged player
                                        Text("Waiting for accept...")
                                        hasGame = true
                                    }
                                } else if (game.player2Id == model.localPlayerId.value && game.gameState == "invite") {
                                    if (game.player1Id == documentId) { // Only show for the challenging player
                                        Row {
                                            // accept and decline a game invite
                                            Button(onClick = { model.acceptGameInvite(gameId) }) {
                                                Text("Accept")
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Button(onClick = { model.declineGameInvite(gameId) }) {
                                                Text("Decline")
                                            }
                                        }
                                        hasGame = true
                                    }
                                }
                            }
                            if (!hasGame) {
                                Button(onClick = {
                                    model.db.collection("games")
                                        .add(Game(gameState = "invite",
                                            player1Id = model.localPlayerId.value!!,
                                            player2Id = documentId))
                                        .addOnSuccessListener { documentRef ->
                                            // Navigate to the GameScreen with the newly created gameId
                                            //navController.navigate("game/${documentRef.id}")
                                        }
                                        .addOnFailureListener { e ->
                                            Log.e("Error", "Error creating game invite: ${e.message}")
                                        }
                                }) {
                                    Text("Challenge")
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(navController: NavController, model: TicTacToeViewModel, gameId: String?) {
    val players by model.playerMap.collectAsStateWithLifecycle()
    val games by model.gameMap.collectAsStateWithLifecycle()

    var playerName = "Unknown?"
    players[model.localPlayerId.value]?.let {
        playerName = it.name
    }

    if (gameId != null && games.containsKey(gameId)) {
        val game = games[gameId]!!
        Scaffold(
            topBar = { TopAppBar(title = { Text("TicTacToe - $playerName") }) },
            containerColor = Color.LightGray
        ) { innerPadding ->
            // to center the Column
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center) // Center the Column for the Box
                ) {
                    when (game.gameState) {
                        "player1_won", "player2_won", "draw" -> {
                            Text("Game over!", style = MaterialTheme.typography.headlineSmall)
                            Spacer(modifier = Modifier.padding(20.dp))

                            if (game.gameState == "draw") {
                                Text("It's a Draw!", style = MaterialTheme.typography.headlineMedium)
                            } else {
                                val winText = if (
                                    (game.gameState == "player1_won" && game.player1Id == model.localPlayerId.value) ||
                                    (game.gameState == "player2_won" && game.player2Id == model.localPlayerId.value)
                                ) {
                                    "You Won!"
                                } else {
                                    "You Lost!"
                                }
                                Text(winText, style = MaterialTheme.typography.headlineMedium)
                            }
                            Button(onClick = {
                                navController.navigate("lobby") // goes back to lobby
                            }) {
                                Text("Back to lobby")
                            }
                        }

                        else -> {
                            val myTurn =
                                game.gameState == "player1_turn" && game.player1Id == model.localPlayerId.value ||
                                        game.gameState == "player2_turn" && game.player2Id == model.localPlayerId.value
                            val turn = if (myTurn) "Your turn!" else "Wait for other player"
                            Text(turn, style = MaterialTheme.typography.headlineMedium)
                            Spacer(modifier = Modifier.padding(20.dp))

                            Text("Player 1: ${players[game.player1Id]!!.name}")
                            Text("Player 2: ${players[game.player2Id]!!.name}")
                            Spacer(modifier = Modifier.padding(20.dp))

                            // Generera en lista för celler och dela upp i rader
                            val cells = (0 until rows * cols).toList()
                            cells.chunked(cols).forEach { row ->
                                Row {
                                    row.forEach { index ->
                                        Button(
                                            shape = CircleShape,
                                            modifier = Modifier
                                                .size(100.dp)
                                                .padding(2.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color.Black,
                                                disabledContainerColor = Color.Black // Keep disabled buttons black
                                            ),
                                            enabled = game.gameBoard[index] == 0, // Disable if cell is already filled
                                            onClick = {
                                                model.checkGameState(gameId, index)
                                            }
                                        ) {
                                            when (game.gameBoard[index]) {
                                                1 -> Icon(
                                                    painter = painterResource(id = R.drawable.outline_cross_24),
                                                    tint = Color.Cyan,
                                                    contentDescription = "X",
                                                    modifier = Modifier.size(48.dp)
                                                )
                                                2 -> Icon(
                                                    painter = painterResource(id = R.drawable.outline_circle_24),
                                                    tint = Color.Green,
                                                    contentDescription = "O",
                                                    modifier = Modifier.size(48.dp)
                                                )
                                                else -> Text("")
                                            }
                                        }
                                    }
                                }
                            }

                            // Give Up button
                            Button(
                                onClick = {
                                    model.giveUp(gameId)
                                    navController.navigate("lobby") // Navigate to lobby after giving up
                                },
                                modifier = Modifier.padding(top = 16.dp) // Add some padding
                            ) {
                                Text("Give Up")
                            }
                        }
                    }
                }
            }
        }
    } else {
        Log.e(
            "Error",
            "Error Game not found: $gameId"
        )
        navController.navigate("lobby")
    }
}
