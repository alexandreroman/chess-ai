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

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
class AIDialogController {
    private final Logger logger = LoggerFactory.getLogger(AIDialogController.class);
    private final ChessEngine chessEngine;
    private final BoardRepository repo;
    private final StringRedisTemplate redis;
    private final ChatClient chatClient;
    private final int maxConversationEntries = 10;

    AIDialogController(ChessEngine chessEngine, BoardRepository repo, StringRedisTemplate redis, ChatClient chatClient) {
        this.repo = repo;
        this.redis = redis;
        this.chatClient = chatClient;
        this.chessEngine = chessEngine;
    }

    @ModelAttribute
    String boardId(@PathVariable String boardId) {
        return boardId;
    }

    @GetMapping("/chess/{boardId}/ai")
    String form(@PathVariable String boardId,
                @RequestParam(name = "show", required = false, defaultValue = "false") boolean showDialog,
                @ModelAttribute("form") AIForm form, Model model) {
        final var q = redis.opsForValue().get("chess::" + boardId + "::question");
        form.setQuestion(q);
        model.addAttribute("showDialog", showDialog);
        return "ai-dialog-form-fragment";
    }

    @PostMapping("/chess/{boardId}/ai/question")
    String postQuestion(@PathVariable String boardId, @ModelAttribute("form") AIForm form) {
        var q = form.getQuestion();
        if (q != null) {
            q = q.strip();
        }
        if (q == null || q.isEmpty()) {
            redis.delete("chess::" + boardId + "::question");
            throw new IllegalArgumentException("Question is empty");
        }
        logger.atDebug().log("Saving question for board {}: {}", boardId, q);
        redis.opsForValue().set("chess::" + boardId + "::question", q);
        return "ai-dialog-fragment";
    }

    @GetMapping(value = "/chess/{boardId}/ai/answer", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    String getAnswer(@PathVariable String boardId) {
        final var q = redis.opsForValue().get("chess::" + boardId + "::question");
        if (q == null) {
            throw new IllegalStateException("No question found for board " + boardId);
        }

        logger.atInfo().log("Asking AI a question related to board {}: {}", boardId, q);
        final var board = repo.load(boardId).orElseThrow();

        final var prompt = new StringBuilder("""
                Process this question from the player (using Markdown only for formatting):
                
                <question>{question}</question>
                
                Answer using the language used to ask the question.
                For example: if the question is in French, answer in French.
                """.trim());
        final var convEntries = redis.opsForList().range("chess::" + boardId + "::conversation", 0, -1);
        if (convEntries != null && !convEntries.isEmpty()) {
            prompt.append("\n\nAlso consider the past questions / answers in chronological order:");
            for (final var c : convEntries) {
                prompt.append("\n\n").append(c);
            }
            prompt.append("\n");
        }

        final var resp = chatClient.prompt()
                .user(p -> p.text(prompt.toString()).param("question", q))
                .tools(new ChessGameTools(board.game(), chessEngine))
                .call()
                .content();

        final var newConvEntry = """
                <question>%s</question>
                <answer>
                %s
                </answer>
                """.formatted(q.trim(), resp.trim()).trim();
        redis.opsForList().rightPush("chess::" + boardId + "::conversation", newConvEntry);
        redis.opsForList().trim("chess::" + boardId + "::conversation", -maxConversationEntries, -1);

        return HtmlRenderer.builder().build().render(Parser.builder().build().parse(resp));
    }

    public static class AIForm {
        private String question;

        public String getQuestion() {
            return question;
        }

        public void setQuestion(String question) {
            this.question = question;
        }

        @Override
        public String toString() {
            return "AIForm{" +
                    "question='" + question + '\'' +
                    '}';
        }
    }
}
