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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class BoardRepository {
    private final Logger logger = LoggerFactory.getLogger(BoardRepository.class);
    private final StringRedisTemplate redis;
    private final String initialFen;

    // This is a core service of the app, as it enables to load / save a board state from a board id.
    // Using this service we rely on Redis to store anything we need, which makes this app stateless.

    BoardRepository(StringRedisTemplate redis,
                    @Value("${app.chess.initial:rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1}") String initialFen) {
        this.redis = redis;
        this.initialFen = initialFen;
    }

    void save(Board board) {
        logger.atTrace().log("Saving board: {}", board);
        if (board.currentSquare() == null) {
            redis.delete("chess::" + board.id() + "::current");
        } else {
            redis.opsForValue().set("chess::" + board.id() + "::current", board.currentSquare());
        }
        if (board.error() == null) {
            redis.delete("chess::" + board.id() + "::error");
        } else {
            redis.opsForValue().set("chess::" + board.id() + "::error", board.error().name());
        }
        redis.delete("chess::" + board.id() + "::moves");
        final var moves = board.game().getNotationList(NotationType.UCI);
        if (!moves.isEmpty()) {
            redis.opsForList().rightPushAll("chess::" + board.id() + "::moves", moves);
        }
    }

    Board newInstance() {
        final var id = UUID.randomUUID().toString();
        logger.atTrace().log("Creating new board: {}", id);
        final var board = new Board(id, new ChessGame(), null, null);
        save(board);
        return board;
    }

    Optional<Board> load(String id) {
        logger.atTrace().log("Loading board: {}", id);
        final var current = redis.opsForValue().get("chess::" + id + "::current");
        final var errorStr = redis.opsForValue().get("chess::" + id + "::error");
        final var movesStr = redis.opsForList().range("chess::" + id + "::moves", 0, -1);
        try {
            final var game = new ChessGame(initialFen);
            if (movesStr != null && game.getInitialFen().equals(ChessGame.STANDARD_INITIAL_FEN)) {
                // This is the core mechanic of this class:
                // we have loaded all the moves from Redis, let's play these moves
                // with a brand-new board instance to get back to the same state.
                game.playMoves(NotationType.UCI, movesStr);
            }

            return Optional.of(new Board(id, game, current, errorStr == null ? null : Board.Error.valueOf(errorStr)));
        } catch (Exception e) {
            logger.atWarn().log("Failed to load board: {}", id, e);
            return Optional.empty();
        }
    }
}
