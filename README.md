# Chess AI

This project implements a chess game using Spring Boot and various AI models.
It provides a web interface for playing against the AI and analyzing games.

https://github.com/user-attachments/assets/bb0ffb6a-f31c-4f65-8a84-a0d633a94c82

## Introduction

This project aims to create a chess game that can be used for playing and game analysis.
It leverages [Spring Boot](https://spring.io/projects/spring-boot) for the backend,
[Spring AI](https://spring.io/projects/spring-ai) for integrating with different AI models,
[HTMX](https://htmx.org/) with [Bootstrap](https://getbootstrap.com/) for the UI, and
[Redis](https://redis.io/) as a persistent storage.

Different AI models can be configured, providing flexibility and allowing for experimentation
with various AI strategies.

## Features

- Play chess against the AI.
- Analyze existing chess games by importing the data to [Lichess.org](https://lichess.org/).
- Configurable AI models (Mistral AI, OpenAI, Gemma, and more).
- Web-based user interface.
- Asynchronous processing for AI moves leveraging WebSocket.
- Integration with Redis for persisting data.

## Getting Started

### Prerequisites

- Java 21 or higher
- Docker Desktop
- An account with the chosen AI provider.

### Building the Project

1. Clone the repository:
   ```shell
   git clone https://github.com/alexandreroman/chess-ai.git
   ```

2. Build the project using Maven:
   ```shell
   cd chess-ai
   ./mvnw clean package
   ```

3. Configure AI API keys (see Configuration section below).

4. Run the application:
   ```shell
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=<AI profile, see below>
   ```

The application has been tested with Mistral AI, OpenAI, Gemma, Llama and Claude.
Gemma and Llama are provided through [Groq Cloud](https://groq.com/)
and its OpenAI compatible endpoint.

Mistral AI is used by default: just set environment variable `MISTRALAI_API_KEY`.

For OpenAI: set environment variable `OPENAI_API_KEY`.

For Claude: set environment variable `ANTHROPIC_API_KEY`.

For other AI models, you need a Groq API key, defined by the environment variable `GROQ_API_KEY`.

Depending on the AI model you want to run, you need to enable the according Spring profile:

* Mistral AI: `mistralai`
* OpenAI: `openai`
* Gemma: `gemma`
* Llama: `llama`
* Claude: `claude`

For instance, run this app with OpenAI:

```shell
export OPENAI_API_KEY=xxx
./mvnw spring-boot:run -Dspring-boot.run.profiles=openai
```

This app also relies on a chess engine to guess the next move to play.
In fact, most AI models are not smart enough to play chess against an human player: the chess engine
is plugged as an AI tool to improve the gaming experience.

[Stockfish.online](https://stockfish.online/) is used as the chess engine for this app.

You may also enable [Chess-API.online](http://chess-api.online/) as an alternative.

You can even choose to disable any chess engine, and rely on the LLM to guess the next move.

Pick your favorite chess engine by setting this environment variable:

* Stockfish.online: `CHESS_ENGINE=stockfishonline`
* Chess-API.online: `CHESS_ENGINE=chessapi`
* None (trust your LLM!): `CHESS_ENGINE=none`

Then run the app with this environment variable:

```shell
export CHESS_ENGINE=none
./mvnw spring-boot:run -Dspring-boot.run.profiles=mistralai
```

Enjoy! 🥳

## Usage

1. Start the application and navigate to `http://localhost:8080` in your web browser.

2. Hit 'Start a new game'

3. Play some chess!

4. Need some help? Hit 'Ask AI' and submit your question.

## Contributing

Contributions are welcome!
If you'd like to contribute to this project, please fork the repository and submit a pull request.

## License

This project is licensed under the Apache License, Version 2.0. See the LICENSE file for details.
