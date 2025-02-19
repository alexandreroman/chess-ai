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

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mistralai.MistralAiChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.annotation.RegisterReflection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.io.Resource;

@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(AIResourceHints.class)
// FIXME workaround for missing metadata in native image for OpenAiChatOptions and MistralAiChatOptions
@RegisterReflection(classes = {ChessGameTools.class, OpenAiChatOptions.class, MistralAiChatOptions.class},
        memberCategories = {MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.DECLARED_FIELDS})
class AIConfig {
    @Bean
    ChatClient chatClient(ChatClient.Builder chatClientBuilder,
                          @Value("classpath:/system-message.st") Resource systemRes) {
        // Configure a ChatClient instance used by the app.
        return chatClientBuilder
                .defaultSystem(systemRes)
                .build();
    }
}
