package com.tictac.id1

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.text.clear

data class Player(
    var name: String = ""
)
data class PlayerStats(
    val playerId: String = "",
    val player: Player,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0
){
    constructor() : this("", Player(), 0, 0, 0) // No-argument constructor
}

data class Game(
    var gameBoard: List<Int> = List(9) { 0 },
    var gameState: String = "invite",
    var player1Id: String = "",
    var player2Id: String = "",
    var statsUpdated: Boolean = false
)

const val rows = 3
const val cols = 3

class TicTacToeViewModel: ViewModel() {
    val db = Firebase.firestore
    var localPlayerId = mutableStateOf<String?>(null)
    val playerMap = MutableStateFlow<Map<String, Player>>(emptyMap())
    val gameMap = MutableStateFlow<Map<String, Game>>(emptyMap())


    fun addNewPlayerStats(playerId: String, player: Player) {
        viewModelScope.launch {
            try {
                val initialPlayerStats = PlayerStats(playerId = playerId, player = player, wins = 0, losses = 0, draws = 0)
                db.collection("playerStats").document(playerId).set(initialPlayerStats).await()
                Log.d("TicTacToeViewModel", "New PlayerStats added for playerId: $playerId")
            } catch (e: Exception) {
                Log.e("TicTacToeViewModel", "Error adding new PlayerStats: ${e.message}")
            }
        }
    }

    fun updatePlayerStats(gameId: String) {
        viewModelScope.launch {  val game = gameMap.value[gameId]

            if (game != null && !game.statsUpdated) { // Check the flag
                when (game.gameState) {
                    "player1_won" -> {
                        incrementStat(game.player1Id, "wins")
                        incrementStat(game.player2Id, "losses")
                    }
                    "player2_won" -> {
                        incrementStat(game.player2Id, "wins")
                        incrementStat(game.player1Id, "losses")
                    }
                    "draw" -> {
                        incrementStat(game.player1Id, "draws")
                        incrementStat(game.player2Id, "draws")
                    }
                }

                // Update the flag in Firebase
                db.collection("games").document(gameId)
                    .update("statsUpdated", true)
                    .addOnSuccessListener {
                        // Update the flag in the local gameMap
                        gameMap.value = gameMap.value + (gameId to game.copy(statsUpdated = true))
                    }
                    .addOnFailureListener { e ->
                        Log.e("Error", "Error updating statsUpdated flag: ${e.message}")
                    }
            }
        }
    }

    private suspend fun incrementStat(playerId: String, statName: String) {
        try {
            db.collection("playerStats").document(playerId)
                .get()
                .await()
                .let { document ->
                    val currentStats = document.toObject(PlayerStats::class.java)
                        ?: PlayerStats(playerId = playerId, player = Player()) // Create with empty Player object
                    val updatedStats = when (statName) {
                        "wins" -> currentStats.copy(wins = currentStats.wins + 1)
                        "losses" -> currentStats.copy(losses = currentStats.losses + 1)
                        "draws" -> currentStats.copy(draws = currentStats.draws + 1)
                        else -> currentStats
                    }
                    db.collection("playerStats").document(playerId)
                        .set(updatedStats)
                        .await()
                }
        } catch (e: Exception) {
            Log.e("Error", "Error updating player stats: ${e.message}")
        }
    }




    fun loadPlayers() {
        // listen for players and games.Then updates playerMap and gameMap in realtime in database,
        db.collection("players")
            .addSnapshotListener { value, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                if (value != null) {
                    val updatedMap = value.documents.associate { doc ->
                        doc.id to doc.toObject(Player::class.java)!!
                    }
                    playerMap.value = updatedMap
                }
            }


        db.collection("games")
            .addSnapshotListener { value, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                if (value != null) {
                    val updatedMap = value.documents.associate { doc ->
                        doc.id to doc.toObject(Game::class.java)!!
                    }
                    gameMap.value = updatedMap
                }
            }
    }


    fun acceptGameInvite(gameId: String) {
        viewModelScope.launch {
            try {
                db.collection("games").document(gameId)
                    .update("gameState", "player1_turn")
                    .await()// waits for a response
                Log.d("TicTacToeViewModel", "Game invite accepted for gameId: $gameId")
            } catch (e: Exception) {
                Log.e("TicTacToeViewModel", "Error accepting game invite: ${e.message}")
            }
        }
    }


    fun declineGameInvite(gameId: String) {
        viewModelScope.launch {
            try {
                // removes the game document from firebase after declining a invite
                db.collection("games").document(gameId)
                    .delete()
                    .await()//waits for response

            } catch (e: Exception) {
                Log.e("Error", "Error declining game invite: ${e.message}")
            }
        }
    }


    fun checkWinner(board: List<Int>): Int {
        // Check rows
        for (i in 0..2) {
            if (board[i * 3] != 0 && board[i * 3] == board[i * 3 + 1] && board[i * 3] == board[i * 3 + 2]) {
                return board[i * 3]
            }
        }

        // Check columns
        for (i in 0..2) {
            if (board[i] != 0 && board[i] == board[i + 3] && board[i] == board[i + 6]) {
                return board[i]
            }
        }

        // Check diagonals
        if (board[0] != 0 && board[0] == board[4] && board[0] == board[8]) {
            return board[0]
        }
        if (board[2] != 0 && board[2] == board[4] && board[2] == board[6]) {
            return board[2]
        }

        // Check draw
        if (!board.contains(0)) { // Check if all cells are filled and no winner
            return 3
        }

        // No winner yet
        return 0
    }


    fun checkGameState(gameId: String, cell: Int) {
        viewModelScope.launch {

            val game: Game? = gameMap.value[gameId]
            if (game != null) {
                val myTurn = game.gameState == "player1_turn" && game.player1Id == localPlayerId.value ||
                        game.gameState == "player2_turn" && game.player2Id == localPlayerId.value
                if (!myTurn) return@launch

                // Check if cell index is valid and cell is empty
                if (cell in 0 until game.gameBoard.size && game.gameBoard[cell] == 0) {
                    val list: MutableList<Int> = game.gameBoard.toMutableList()

                    if (game.gameState == "player1_turn") {
                        list[cell] = 1
                    } else if (game.gameState == "player2_turn") {
                        list[cell] = 2
                    }


                    var turn = if (game.gameState == "player1_turn") "player2_turn" else "player1_turn"
                    val winner = checkWinner(list)
                    if (winner == 1) {
                        turn = "player1_won"
                    } else if (winner == 2) {
                        turn = "player2_won"
                    } else if (winner == 3) {
                        turn = "draw"
                    }



                    db.collection("games").document(gameId)
                        .update(
                            mapOf(
                                "gameBoard" to list,
                                "gameState" to turn
                            )
                        )
                        .addOnSuccessListener {
                            gameMap.value = gameMap.value + (gameId to game.copy(gameBoard = list, gameState = turn))

                            if (turn == "player1_won" || turn == "player2_won" || turn == "draw") {
                                updatePlayerStats(gameId) // Call updatePlayerStats here
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("Error", "Error updating game: ${e.message}")
                        }

                } else {
                    Log.e("Error", "Invalid cell index: $cell")
                }
            }

        }
    }



    fun giveUp(gameId: String) {
        viewModelScope.launch {

            val game = gameMap.value[gameId]

            if (game != null) {
                val winner = if (game.player1Id == localPlayerId.value) {
                    "player2_won" // Player 2 wins if player 1 gives up
                } else {
                    "player1_won" // Player 1 wins if player 2 gives up
                }

                db.collection("games").document(gameId)
                    .update("gameState", winner)
                    .addOnSuccessListener {
                        // Updates gameMap
                        gameMap.value = gameMap.value + (gameId to game.copy(gameState = winner))

                        updatePlayerStats(gameId)
                    }
                    .addOnFailureListener { e ->
                        Log.e("Error", "Error updating game state after give up: ${e.message}")
                    }

            }
        }
    }

    fun rematch(gameId: String) {
        viewModelScope.launch {
            try {
                // Update game state to "player1_turn" or "invite"
                db.collection("games").document(gameId)
                    .update(
                        mapOf(
                            "gameBoard" to List(9) { 0 }, // Reset game board
                            "gameState" to "player1_turn" // Or "invite" if you want to start with an invite
                        )
                    )
                    .await()
                Log.d("TicTacToeViewModel", "Rematch initiated for gameId: $gameId")
            } catch (e: Exception) {
                Log.e("TicTacToeViewModel", "Error initiating rematch: ${e.message}")
            }


            db.collection("games").document(gameId)
                .update("statsUpdated", false) // Reset to false for rematch
                .await()
        }
    }

}


