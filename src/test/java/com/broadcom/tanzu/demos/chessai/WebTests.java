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

import io.github.bonigarcia.wdm.WebDriverManager;
import io.github.wolfraam.chessgame.board.Square;
import io.github.wolfraam.chessgame.move.Move;
import org.junit.jupiter.api.*;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.openqa.selenium.support.ui.ExpectedConditions.attributeContains;
import static org.openqa.selenium.support.ui.ExpectedConditions.numberOfElementsToBe;

@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "debug=true"
})
class WebTests {
    @LocalServerPort
    private int port;
    @MockitoBean
    private ChessEngine chessEngine;

    private WebDriver driver;
    private WebDriverWait wait;

    @BeforeAll
    public static void init() {
        WebDriverManager.chromedriver().setup();
    }

    @BeforeEach
    public void setUp() {
        final var driverOptions = new ChromeOptions();
        //driverOptions.addArguments("--headless");
        driver = new ChromeDriver(driverOptions);
        driver.manage().timeouts().implicitlyWait(Duration.ofMillis(500));
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    @AfterEach
    public void tearDown() {
        if (wait != null) {
            wait = null;
        }
        if (driver != null) {
            driver.quit();
            driver = null;
        }
    }

    @Test
    void testRedirection() {
        driver.get(uri("/"));
        assertThat(driver.getCurrentUrl()).isEqualTo(uri("/chess"));
    }

    @Test
    @Disabled("Need a local LLM which can deal with function calling and structured output")
    void testPlayGame() throws IOException {
        driver.get(uri("/chess"));
        driver.findElement(By.id("new-game")).click();
        assertThat(driver.getCurrentUrl()).startsWith(uri("/chess/"));

        driver.findElement(By.xpath("//p[text()='Waiting for player...']"));
        driver.findElement(By.id("square-d2")).click();
        wait.until(attributeContains(By.id("square-d2"), "class", "current"));
        wait.until(attributeContains(By.cssSelector("#square-d3 svg"), "class", "legal-move"));
        wait.until(attributeContains(By.cssSelector("#square-d4 svg"), "class", "legal-move"));

        refreshPage();

        wait.until(attributeContains(By.cssSelector("#square-d3 svg"), "class", "legal-move"));
        wait.until(attributeContains(By.cssSelector("#square-d4 svg"), "class", "legal-move"));

        when(chessEngine.getNextMove(any())).thenReturn(Optional.of(new Move(Square.fromName("e2"), Square.fromName("e4"))));
        when(chessEngine.toString()).thenReturn("ChessEngine Mock");

        driver.findElement(By.id("square-d4")).click();

        wait.until(attributeContains(By.cssSelector("#square-d4 svg"), "class", "fa-chess-pawn"));
        assertThat(driver.findElement(By.id("square-d2")).findElements(By.name("svg"))).isEmpty();
        verify(chessEngine, timeout(Duration.ofSeconds(60).toMillis()).atLeastOnce()).getNextMove(any());

        try {
            wait.until(numberOfElementsToBe(By.xpath("//p[text()='AI is thinking...']"), 1));
        } finally {
            File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            File destination = new File("screenshot.png");
            Files.copy(screenshot.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void refreshPage() {
        driver.get(driver.getCurrentUrl());
    }

    private String uri(String path) {
        return UriComponentsBuilder.fromUriString("http://localhost:%d".formatted(port))
                .path(path).build().toUriString();
    }
}
