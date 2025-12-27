# Course RAG Backend

This is the backend service for the Course RAG (Retrieval-Augmented Generation) application. It leverages Spring Boot, LangChain4j, and ChromaDB to provide intelligent Q&A capabilities over your course materials using local LLMs.

## 🚀 How it Works

The backend operates on a RAG pipeline:
1.  **Ingestion**: Documents (PDFs, etc.) are parsed using Apache Tika.
2.  **Embedding**: Text chunks are converted into vector embeddings using a local embedding model (e.g., `nomic-embed-text-v1.5`) running on Ollama or LM Studio.
3.  **Storage**: These vectors are stored in **ChromaDB**, a vector database.
4.  **Retrieval & Generation**: When a user asks a question, the system searches ChromaDB for relevant context and feeds it, along with the question, to a local LLM (e.g., `meta-llama-3-8b-instruct`) to generate an accurate answer based *strictly* on the course material.

## 🛠️ Configuration & Customization

To make this backend work for your specific setup, you may need to modify `src/main/resources/application.properties`.

### Key Configuration Properties

*   **LLM Provider URL**:
    ```properties
    langchain4j.openai.base-url=http://localhost:11434/v1
    ```
    *Change this if your LM Studio instance is running on a different host or port.*

*   **Models**:
    ```properties
    langchain4j.openai.chat-model.model-name=meta-llama-3-8b-instruct
    langchain4j.openai.embedding-model.model-name=text-embedding-nomic-embed-text-v1.5
    ```
    *Ensure these model names match exactly what you have pulled/installed in your local LLM provider.*

*   **ChromaDB**:
    ```properties
    langchain4j.chroma.embedding-store.base-url=http://localhost:8005
    ```
    *This points to the ChromaDB service. If running via Docker Compose, it defaults to `http://chromadb:8000` inside the container network.*

## 📦 Getting Started

### Prerequisites
*   Java 17+
*   Docker & Docker Compose
*   LM Studio (running locally)

### Running with Docker (Recommended)

1.  Make sure your local LLM provider (LM Studio) is running.
2.  Build and start the services:
    ```bash
    docker-compose up --build
    ```
    This will start both the Backend (on port `8080`) and ChromaDB (on port `8005`).

### Running Locally (Maven)

1.  Start ChromaDB (e.g., via Docker: `docker run -p 8005:8000 chromadb/chroma:0.5.0`).
2.  Run the Spring Boot application:
    ```bash
    ./mvnw spring-boot:run
    ```

## 💻 Client Application

To interact with this backend, you need the frontend client.

You can go to the **course-rag-client** to install the client via the repo:
👉 **https://github.com/B-Zakaria-u/course-rag-client**
You can also install the full desktop app from the repo:
👉 **https://github.com/B-Zakaria-u/unified-desktop-shell**

