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

package com.broadcom.tanzu.demos.chessai.impl.chessapi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.chess.engine", havingValue = "chessapi")
class ChessApiEngineConfig {
    @Bean
    ChessApi chessApi(RestClient.Builder clientBuilder,
                      @Value("${app.chess-api.url}") String chessApiUrl) {
        final var client = clientBuilder
                .clone()
                .requestFactory(new HttpComponentsClientHttpRequestFactory())
                .baseUrl(chessApiUrl)
                .build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(client))
                .build().createClient(ChessApi.class);
    }

    @Bean
    ChessApiEngine chessEngine(ChessApi chessApi) {
        return new ChessApiEngine(chessApi);
    }
}
