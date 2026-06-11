package com.filesync.server.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.filesync.common.dto.SyncActionDto;
import com.filesync.common.dto.SyncRequestDto;
import com.filesync.server.domain.SyncTask;
import com.filesync.server.repository.SyncTaskRepository;
import com.filesync.server.service.PermissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import com.filesync.server.dto.SyncMessage;
import com.filesync.server.config.RabbitMQConfig;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/sync")
public class SyncController {
    private final SyncTaskRepository taskRepository;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;
    private final PermissionService permissionService;
    private static final Logger log = LoggerFactory.getLogger(SyncController.class);

    public SyncController(SyncTaskRepository taskRepository, ObjectMapper objectMapper,
                          PermissionService permissionService, RabbitTemplate rabbitTemplate)
    {
        this.taskRepository = taskRepository;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.permissionService = permissionService;
    }

    // async
    @PostMapping("/start")
    public Map<String, String> startSync(@RequestBody SyncRequestDto requestDto, Authentication authentication)
    {
        String authenticatedUsername = authentication.getName();
        requestDto.setOwnerId(authenticatedUsername);

        if (requestDto.getFolderId() != null) {
            if (!permissionService.canReadFolder(authenticatedUsername, requestDto.getFolderId())) {
                throw new RuntimeException("No access to shared folder");
            }
        }

        // override the ownerId
        requestDto.setOwnerId(authenticatedUsername);
        // create random task id
        String taskId = UUID.randomUUID().toString();
        log.info("Received sync start request for owner={}, creating taskId={}", requestDto.getOwnerId(), taskId);
        // create new SyncTask obj
        SyncTask task = new SyncTask();
        // set value
        task.setTaskId(taskId);
        task.setOwnerId(requestDto.getOwnerId());
        task.setStatus("PENDING");
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        // store in taskRepo
        taskRepository.save(task);
        log.debug("Task saved with status PENDING");
        // call the startSync method
        SyncMessage message = new SyncMessage(taskId, requestDto);
        rabbitTemplate.convertAndSend(RabbitMQConfig.SYNC_EXCHANGE, RabbitMQConfig.SYNC_ROUTING_KEY, message);

        log.info("Async processing started for taskId={}", taskId);
        // return the map of task id
        return Map.of("taskId", taskId);
    }

    // polls this to check progress
    @GetMapping("/status/{taskId}")
    public Map<String, Object> getStatus(@PathVariable("taskId") String taskId)
    {
        log.debug("Status check for taskId={}", taskId);
        // get the task
        SyncTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null)
        {
            log.warn("Task not found: {}", taskId);
            return Map.of("error", "Task not found");
        }
        // create a hashmap to store the result
        Map<String, Object> result = new HashMap<>();

        // add the task id and current status
        result.put("taskId", task.getTaskId());
        result.put("status", task.getStatus());
        log.debug("Current status for taskId={}: {}", taskId, task.getStatus());
        // if the task is completed
        // Ex:
        // {
        //  "taskId": "abc-123",
        //  "status": "COMPLETED",
        //  "actions": [
        //    { "action": "UPLOAD", "fileMetadata": {...} }
        //  ]
        //}
        if ("COMPLETED".equals(task.getStatus()) && task.getActionsJson() != null)
        {
            try
            {
                // parse the JSON string into List of SyncActionDto obj
                List<SyncActionDto> actionDtoList = objectMapper.readValue(task.getActionsJson(),
                        new TypeReference<List<SyncActionDto>>(){}
                );
                result.put("actions", actionDtoList);
                log.info("Returning {} actions for completed taskId={}", actionDtoList.size(), taskId);
            } catch (Exception e)
            {
                log.error("Failed to parse actions for taskId={}", taskId, e);
                result.put("error", "Failed to parse actions");
            }
        }
        if ("FAILED".equals(task.getStatus()))
        {
            log.warn("Task failed: {} - {}", taskId, task.getErrorMessage());
            result.put("errorMessage", task.getErrorMessage());
        }
        return result;
    }
}
