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

import com.redis.testcontainers.RedisContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.devtools.restart.RestartScope;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfig {
    private static final String TC_OLLAMA_LLAMA = "tc-ollama-llama3.2-1b";
    private static final String LLAMA = "llama3.2:1b";
    private final Logger logger = LoggerFactory.getLogger(TestcontainersConfig.class);

    @Bean
    @RestartScope
    @ServiceConnection
    OllamaContainer ollama() throws IOException, InterruptedException {
        final var listImagesCmd = DockerClientFactory.lazyClient()
                .listImagesCmd()
                .withImageNameFilter(TC_OLLAMA_LLAMA)
                .exec();
        if (listImagesCmd.isEmpty()) {
            logger.atInfo().log("Starting Ollama");
            try (final var ollama = new OllamaContainer("ollama/ollama:0.5.11")) {
                ollama.start();
                logger.atInfo().log("Downloading model: {}", LLAMA);
                ollama.execInContainer("ollama", "pull", LLAMA);
                logger.atInfo().log("Saving image Ollama+model: {}", TC_OLLAMA_LLAMA);
                ollama.commitToImage(TC_OLLAMA_LLAMA);
            }
        }
        return new OllamaContainer(
                DockerImageName.parse(TC_OLLAMA_LLAMA)
                        .asCompatibleSubstituteFor("ollama/ollama")
        ).withReuse(true);
    }

    @Bean
    @ServiceConnection
    RedisContainer redisContainer() {
        return new RedisContainer(DockerImageName.parse("redis:7")).withReuse(true);
    }
}
