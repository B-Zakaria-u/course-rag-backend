import os
import requests
import json
import pandas as pd
from datasets import Dataset
from ragas import evaluate
from ragas.metrics import faithfulness, answer_relevancy, context_precision
from langchain_openai import ChatOpenAI
from langchain_community.embeddings import HuggingFaceEmbeddings
from dotenv import load_dotenv
from typing import Any, List, Optional
from langchain_core.outputs import ChatResult

# Load environment variables
load_dotenv()

GROQ_API_KEY = os.getenv("GROQ_API_KEY")
BACKEND_URL = os.getenv("BACKEND_URL", "http://localhost:8080/api/chat")
# Use 'default-user' or existing chat ID for context
CHAT_ID = "evaluation-session"

if not GROQ_API_KEY:
    print("Error: GROQ_API_KEY not found in .env file.")
    exit(1)

# 1. Configuration for RAGAS (using Groq)
# Groq LLM (Replacing Grok)
# Wrapper to handle n > 1 (Groq limitation)
class GroqClient(ChatOpenAI):
    def _generate(
        self,
        messages: List[Any],
        stop: Optional[List[str]] = None,
        run_manager: Any = None,
        **kwargs: Any,
    ) -> ChatResult:
        n = kwargs.pop("n", 1)
        if n > 1:
            generations = []
            for _ in range(n):
                result = super()._generate(messages, stop, run_manager, **kwargs)
                generations.extend(result.generations)
            return ChatResult(generations=generations)
        return super()._generate(messages, stop, run_manager, **kwargs)

# Recommended for Eval: llama-3.1-70b-versatile or llama3-70b-8192
groq_llm = GroqClient(
    model="llama-3.1-70b-versatile",
    api_key=GROQ_API_KEY,
    base_url="https://api.groq.com/openai/v1",
    max_tokens=512,
    temperature=0
)

# Local Embeddings (free, widely used)
embeddings = HuggingFaceEmbeddings(model_name="sentence-transformers/all-MiniLM-L6-v2")

# 2. Define Test Dataset
# Format: {'question': '...', 'ground_truth': '...'}
test_data = [
    {
        "question": "What is the purpose of the IngestionService?",
        "ground_truth": "The IngestionService handles parsing documents (PDFs, etc.), splitting them into chunks, creating embeddings, and storing them in ChromaDB."
    },
    {
        "question": "How does the ChatService retrieve information?",
        "ground_truth": "The ChatService uses an EmbeddingStoreContentRetriever to find relevant document chunks from ChromaDB based on vector similarity."
    },
    {
        "question": "Which framework is used for RAG orchestration?",
        "ground_truth": "The application uses LansgChain4j for RAG orchestration."
    }
]

# 3. Query Backend to populate 'answer' and 'contexts'
evaluation_dataset = {
    "question": [],
    "answer": [],
    "contexts": [],
    "ground_truth": []
}

print(f"Starting evaluation with {len(test_data)} sample questions...")

for item in test_data:
    question = item["question"]
    ground_truth = item["ground_truth"]
    
    print(f"\nProcessing Question: {question}")
    
    try:
        # Call Backend
        response = requests.post(
            BACKEND_URL,
            params={"chatId": CHAT_ID},
            data=question, # Backend endpoint expects raw body string based on analysis
            timeout=120
        )
        
        response.raise_for_status()
        data = response.json()
        
        answer = data.get("answer", "")
        sources = data.get("sources", [])
        
        # Extract contexts from sources
        contexts = [source.get("excerpt", "") for source in sources]
        
        print(f"Answer: {answer[:100]}...")
        print(f"Retrieved {len(contexts)} content chunks.")

        evaluation_dataset["question"].append(question)
        evaluation_dataset["answer"].append(answer)
        evaluation_dataset["contexts"].append(contexts)
        evaluation_dataset["ground_truth"].append(ground_truth)
        
    except Exception as e:
        print(f"Error querying backend: {e}")

# 4. Run RAGAS Evaluation
# Verify dataset is not empty
if len(evaluation_dataset["question"]) == 0:
    print("\n[ERROR] No samples were collected. Please check the backend connection and ensure it is running.")
    exit(1)

print("\nRunning RAGAS Metrics (this may take a moment)...")

# Convert to HuggingFace Dataset
dataset = Dataset.from_dict(evaluation_dataset)
print(f"Dataset created with {len(dataset)} samples.")

# Run evaluation with custom LLM and Embeddings
results = evaluate(
    dataset=dataset,
    metrics=[
        faithfulness,
        answer_relevancy,
        context_precision
    ],
    llm=groq_llm,
    embeddings=embeddings
)

# 5. Output Results
print("\n=== Evaluation Results ===")
print(results)

df = results.to_pandas()
df.to_csv("results.csv", index=False)
print("\nDetailed results saved to 'results.csv'")
