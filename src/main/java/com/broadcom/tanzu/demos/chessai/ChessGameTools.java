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

import io.github.wolfraam.chessgame.ChessGame;
import io.github.wolfraam.chessgame.notation.NotationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tools used by the LLM to answer player questions and find out the next move to play.
 * Those tools provide additional context for the LLM, giving hints about the current game.
 */
class ChessGameTools {
    private final Logger logger = LoggerFactory.getLogger(com.broadcom.tanzu.demos.chessai.ChessGameTools.class);
    private final ChessGame game;
    private final ChessEngine chessEngine;

    ChessGameTools(ChessGame game, ChessEngine chessEngine) {
        this.game = game;
        this.chessEngine = chessEngine;
    }

    @Tool(description = """
            Get the occupied squares on the board.
            This tool returns the in-game pieces by square: if a piece is not present in the return value,
            this means it has been captured.
            
            A square is defined by a column letter (from A to H) and the row number (from 1 to 8)
            of a chess board.
            For example: A1 is the bottom left square of a chess board.
            
            A piece defines the side (WHITE or BLACK) and the piece type
            (PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING).
            For example: WHITE_KNIGHT defines a Knight piece for the white.
            
            For each entry, you get a square and the piece it occupies.
            For example:
              A2 -> WHITE_PAWN
              G1 -> WHITE_KNIGHT
            """)
    Map<String, String> getOccupiedSquares() {
        final var squares = new HashMap<String, String>(16);
        for (final var sq : game.getOccupiedSquares()) {
            squares.put(sq.name(), game.getPiece(sq).name());
        }
        logger.atTrace().log("Getting occupied squares: {}", squares);
        return squares;
    }

    @Tool(description = """
            Guess the next move.
            
            This tool returns the best move to play using Universal Chess Interface (UCI) format.
            This tool returns 'null' if the next move to play is unknown.
            """)
    String guessNextMove() {
        logger.atTrace().log("About to use {} to guess next move", chessEngine);
        final var move = chessEngine.getNextMove(game)
                .map(m -> game.getNotation(NotationType.UCI, m))
                .orElse(null);
        logger.atTrace().log("Guessed next move: {}", move);
        return move;
    }

    @Tool(description = """
            Get a list of moves played in the game.
            Each move is defined using the Universal Chess Interface (UCI) format.
            Use this tool to track which piece has moved during the game.
            
            The list of UCI moves is returned in the order they were played in the game.
            For example: ["e2e3", "g8f6", "e2e4", "e7e5", "f1h3", "a7a5"].
            """)
    List<String> getPlayedMoves() {
        final var moves = game.getMoves().stream().map(m -> game.getNotation(NotationType.UCI, m)).collect(Collectors.toList());
        logger.atTrace().log("Played moves: {}", moves);
        return moves;
    }

    @Tool(description = """
            Check if a move is actually legal.
            The move is defined using the Universal Chess Interface (UCI) format.
            """)
    boolean isLegalMove(String move) {
        final var legal = game.isLegalMove(game.getMove(NotationType.UCI, move));
        logger.atTrace().log("Is move {} legal? {}", move, legal ? "Yes." : "No.");
        return legal;
    }

    @Tool(description = """
            Get the side whose turn it is to move for the current chess game.
            This tool returns BLACK, WHITE or 'null' if the game is done.
            """)
    String getSideToMove() {
        if (game.getGameResult() != null) {
            logger.atTrace().log("Reading side to move but the game is done");
            return null;
        }
        final var side = game.getSideToMove().name();
        logger.atTrace().log("Reading side to move from current game: {}", side);
        return side;
    }

    @Tool(description = """
            Check if the game is done.
            Use this tool to see if a move can be played.
            If the game is done, no more move can be played.
            """)
    boolean isGameDone() {
        final var done = game.getGameResultType() != null;
        logger.atTrace().log("Is game done? {}", done ? "Yes." : "No.");
        return done;
    }

    @Tool(description = """
            Get the Portable Game Notation (PGN) data from the board.
            Use this tool to analyze all the moves played in the game.
            """)
    String getPGNData() {
        final var pgn = ChessGameUtils.getPGNData(game);
        logger.atTrace().log("Getting PGN data: {}", pgn);
        return pgn;
    }

    @Tool(description = """
            Get the game result of the chess game.
            Consider calling this tool to figure out if the game is still in progress,
            or to see if black or white has won.
            This tool returns the following values:
            - DRAW
            - BLACK_WINS
            - WHITE_WINS
            - IN_PROGRESS
            """)
    String getGameResult() {
        final var gameResult = game.getGameResultType();
        if (gameResult == null) {
            logger.atTrace().log("Reading game result: game is in progress");
            return "IN_PROGRESS";
        }
        final var r = gameResult.name();
        logger.atTrace().log("Reading game result from current game: {}", r);
        return r;
    }
}
