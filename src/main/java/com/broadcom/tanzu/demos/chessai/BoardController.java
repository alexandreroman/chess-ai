/*
 * Copyright (c) 2025 Broadcom, Inc. or its affiliates
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.broadcom.tanzu.demos.chessai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.github.wolfraam.chessgame.board.PieceType;
import io.github.wolfraam.chessgame.board.Side;
import io.github.wolfraam.chessgame.board.Square;
import io.github.wolfraam.chessgame.move.Move;
import io.github.wolfraam.chessgame.notation.NotationType;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;

@Controller
@RegisterReflectionForBinding({BoardController.ChessEvent.class, BoardController.ChessBestMove.class})
class BoardController {
    private final Logger logger = LoggerFactory.getLogger(BoardController.class);
    private final ChessEngine chessEngine;
    private final BoardRepository repo;
    private final SimpMessagingTemplate stomp;
    private final ChatClient chatClient;
    private final TaskExecutor taskExecutor;

    BoardController(ChessEngine chessEngine, BoardRepository repo, SimpMessagingTemplate stomp, ChatClient chatClient, TaskExecutor taskExecutor) {
        this.chessEngine = chessEngine;
        this.repo = repo;
        this.stomp = stomp;
        this.chatClient = chatClient;
        this.taskExecutor = taskExecutor;
    }

    @ModelAttribute("model")
    String model(@Value("${app.model}") String model) {
        return model;
    }

    @GetMapping("/chess")
    String home() {
        return "start";
    }

    @PostMapping("/chess/new")
    String newGame(Model model) {
        // Create a new board instance and redirect to the board page.
        logger.atInfo().log("Starting new game");
        final var board = repo.newInstance();
        return "redirect:/chess/" + board.id();
    }

    @GetMapping("/chess/{boardId}")
    String board(@PathVariable String boardId, Model model, HttpServletResponse resp) {
        logger.atDebug().log("Rendering board page: {}", boardId);
        final var board = repo.load(boardId).orElseThrow();
        model.addAttribute("board", board);

        // Ask browser not to cache nor store the page.
        resp.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());

        return "board";
    }

    @GetMapping("/chess/{boardId}/board")
    String boardFragment(@PathVariable String boardId, Model model, HttpServletResponse resp) {
        // This method is called by HTMX to update the board state.
        logger.atDebug().log("Rendering board fragment: {}", boardId);
        final var board = repo.load(boardId).orElseThrow();
        model.addAttribute("board", board);

        // Ask browser not to cache nor store this fragment.
        resp.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());

        return "board-fragment";
    }

    @PostMapping("/chess/{boardId}/click/{square}")
    String click(@PathVariable String boardId, @PathVariable("square") String squareStr, Model model, HttpServletResponse resp) {
        logger.atDebug().log("Rendering board after clicking on square {}: {}", squareStr, boardId);
        final var square = Square.fromName(squareStr);
        final var board = repo.load(boardId).orElseThrow();
        model.addAttribute("board", board);

        Board newBoard = null;
        boolean triggerAI = false;

        logger.atTrace().log("Board game result: {}={}", boardId, board.game().getGameResult());

        // Main game loop: what happens when the player click on a square?
        if (board.game().getGameResultType() == null) {
            // OK, so the game is not done yet.
            if (board.game().getSideToMove().equals(Side.WHITE)) {
                if (board.currentSquare() == null) {
                    // The player has clicked on a square, let's see if there is a White piece on it.
                    final var piece = board.game().getPiece(square);
                    if (piece != null && piece.side.equals(board.game().getSideToMove())) {
                        newBoard = new Board(boardId, board.game(), square.name, null);
                    }
                } else {
                    // At this point we know that the player has previously selected a piece:
                    // let's see if we can move this piece to the selected square.
                    boolean promotion = false;
                    final var sq = Square.fromName(board.currentSquare());
                    final var sourcePiece = board.game().getPiece(sq);
                    final var targetPiece = board.game().getPiece(square);
                    if (sourcePiece != null && sourcePiece.pieceType.equals(PieceType.PAWN) && targetPiece == null && sq.y == 6) {
                        // TODO implement pawn promotion
                        promotion = true;
                    }

                    final var move = new Move(Square.fromName(board.currentSquare()), square, promotion ? PieceType.QUEEN : null);
                    if (board.game().isLegalMove(move)) {
                        // This is a legal move, moving on!
                        logger.atInfo().log("Playing user move on board {}: {}", boardId, board.game().getNotation(NotationType.UCI, move));
                        board.game().playMove(move);
                        triggerAI = true;
                    }
                    newBoard = new Board(boardId, board.game(), null, null);
                }
            }
        }

        if (newBoard != null) {
            logger.atDebug().log("Saving board: {}", newBoard.id());
            repo.save(newBoard);
            model.addAttribute("board", newBoard);
        }

        if (triggerAI) {
            // Now it's time for AI to play!
            taskExecutor.execute(() -> {
                try {
                    playNextMoveForBlack(boardId);
                } catch (Exception e) {
                    final Board.Error error;
                    if (e instanceof AIMoveError) {
                        error = ((AIMoveError) e).error;
                    } else {
                        error = Board.Error.SERVER_ERROR;
                    }
                    repo.save(new Board(board.id(), board.game(), null, error));
                    refreshBoardUI(boardId);
                    logger.atWarn().log("Failed to play next move for AI", e);
                }
            });
        }

        // Ask browser not to cache nor store this fragment.
        resp.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());

        return "board-fragment";
    }

    private void playNextMoveForBlack(String boardId) {
        final var board = repo.load(boardId).orElseThrow();
        if (!board.game().getSideToMove().equals(Side.BLACK)) {
            logger.atWarn().log("Skipping next move for black side using board: {}", boardId);
            return;
        }

        // Trigger the LLM: let's find out the next move to play.
        logger.atDebug().log("Guessing next move using chess game tools for board: {}", boardId);
        var resp = chatClient.prompt()
                .user("""
                        What is the next move to play in this chess game?
                        Answer with one move only using UCI notation (source square-target square).
                        Do not include anything else in your answer.
                        """)
                // Include additional tools that the LLM can use to identify the next move.
                .tools(new ChessGameTools(board.game(), chessEngine))
                .call().entity(ChessBestMove.class);
        if (resp == null || resp.isNull()) {
            logger.atDebug().log("Failed to get next move using chess game tools, trying with a FEN only for board: {}", boardId);
            resp = chatClient.prompt()
                    .user(p -> p.text("""
                                    You're playing a chess game: you're playing Black.
                                    Consider this FEN as the current board state (surrounded by a fen tag): <fen>{fen}</fen>
                                    
                                    Here are the board state and past moves using PGN (surrounded by a pgn tag):
                                    <pgn>
                                    {pgn}
                                    </pgn>
                                    
                                    What is the next move to play?
                                    Answer with one move only using UCI notation (source square-target square).
                                    Do not include anything else in your answer.
                                    """)
                            .param("fen", board.game().getFen())
                            .param("pgn", ChessGameUtils.getPGNData(board.game())))
                    .call().entity(ChessBestMove.class);
            if (resp == null || resp.isNull()) {
                // The LLM failed to identify the next move: this may happen if the game is done,
                // if the chess engine is unable to provide the next move, or if the LLM failed to
                // use the tools and cannot identify the move by itself.
                logger.atDebug().log("No best move found for board {}", boardId);
                throw new AIMoveError(Board.Error.UNABLE_TO_GUESS_NEXT_MOVE, null);
            }
        }
        logger.atInfo().log("Playing AI move on board {}: {}", boardId, resp.bestMove);
        final Move move;
        try {
            // Find out if the LLM move does use UCI.
            move = board.game().getMove(NotationType.UCI, resp.bestMove);
        } catch (Exception e) {
            logger.atDebug().log("Unable to parse move from AI for board {}: {}", boardId, resp.bestMove);
            throw new AIMoveError(Board.Error.ILLEGAL_MOVE_FROM_AI, e);
        }
        if (!board.game().isLegalMove(move)) {
            // During late game (and without a chess engine) the LLM sometimes makes illegal moves.
            logger.atDebug().log("Invalid move from AI for board {}: {}", boardId, resp.bestMove);
            throw new AIMoveError(Board.Error.ILLEGAL_MOVE_FROM_AI, null);
        }

        // Great, the AI has a move to play: let's update the board.
        logger.atDebug().log("Playing next move from AI on board {}: {}", board.id(), resp.bestMove);
        board.game().playMove(move);
        repo.save(new Board(board.id(), board.game(), null, null));
        refreshBoardUI(boardId);
    }

    private void refreshBoardUI(String boardId) {
        logger.atDebug().log("Refreshing board UI: {}", boardId);
        stomp.convertAndSend("/topic/chess/" + boardId, new ChessEvent("UPDATE_BOARD"));
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<?> handleNoSuchElementException(NoSuchElementException e) {
        return ResponseEntity.notFound().build();
    }

    record ChessEvent(String type) {
    }

    record ChessBestMove(@JsonPropertyDescription("""
            Best move to play in Universal Chess Interface (UCI) format.
            The value is 'null' if the next move to play is undefined or unknown.
            """) String bestMove) {
        boolean isNull() {
            return bestMove == null || "null".equals(bestMove);
        }
    }

    class AIMoveError extends RuntimeException {
        private final Board.Error error;

        AIMoveError(Board.Error error, Throwable cause) {
            super("AI move error: " + error, cause);
            this.error = error;
        }
    }
}
