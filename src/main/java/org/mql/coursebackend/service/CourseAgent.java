package org.mql.coursebackend.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;

public interface CourseAgent {
    Result<String> chat(@MemoryId String memoryId, @UserMessage String userMessage);
}
