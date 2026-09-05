package ink.icoding.wechat.article.schedule;

import ink.icoding.wechat.article.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
public class ScheduleTaskController {
    private final ScheduleTaskService service;

    public ScheduleTaskController(ScheduleTaskService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<ScheduleTask>> list() { return ApiResponse.ok(service.list()); }

    @GetMapping("/{id}")
    public ApiResponse<ScheduleTask> get(@PathVariable Long id) { return ApiResponse.ok(service.required(id)); }

    @PostMapping
    public ApiResponse<ScheduleTask> create(@Valid @RequestBody ScheduleTaskService.TaskRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ScheduleTask> update(@PathVariable Long id, @Valid @RequestBody ScheduleTaskService.TaskRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PostMapping("/{id}/run")
    public ApiResponse<TaskRun> run(@PathVariable Long id) { return ApiResponse.ok(service.run(id)); }

    @GetMapping("/{id}/runs")
    public ApiResponse<List<TaskRun>> runs(@PathVariable Long id) { return ApiResponse.ok(service.runs(id)); }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) { service.delete(id); return ApiResponse.ok(); }
}
