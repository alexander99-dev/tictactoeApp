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

data class Player(
    var name: String = ""
)

data class Game(
    var gameBoard: List<Int> = List(9) { 0 },
    var gameState: String = "invite",
    var player1Id: String = "",
    var player2Id: String = ""
)

const val rows = 3
const val cols = 3

class TicTacToeViewModel: ViewModel() {
    val db = Firebase.firestore
    var localPlayerId = mutableStateOf<String?>(null)
    val playerMap = MutableStateFlow<Map<String, Player>>(emptyMap())
    val gameMap = MutableStateFlow<Map<String, Game>>(emptyMap())


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
                    }
                    .addOnFailureListener { e ->
                        Log.e("Error", "Error updating game state after give up: ${e.message}")
                    }
            }
        }
    }
}


