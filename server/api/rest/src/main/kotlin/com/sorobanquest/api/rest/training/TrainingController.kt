package com.sorobanquest.api.rest.training

import com.sorobanquest.api.rest.common.PageResponse
import com.sorobanquest.identity.api.AuthenticatedUser
import com.sorobanquest.shared.model.PageRequest
import com.sorobanquest.shared.model.SessionId
import com.sorobanquest.training.api.StartSessionCommand
import com.sorobanquest.training.api.SubmitAnswerCommand
import com.sorobanquest.training.api.TrainingService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Тренировки", description = "Каталог тренировок и жизненный цикл тренировочной сессии")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/training")
class TrainingController(
    private val training: TrainingService,
) {

    @Operation(summary = "Список доступных видов тренировок")
    @GetMapping("/drills")
    fun drills(): List<DrillResponse> = training.listDrills().map(DrillResponse::of)

    @Operation(summary = "Начать тренировку. В ответе приходит первое задание")
    @PostMapping("/sessions")
    fun start(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @Valid @RequestBody request: StartSessionRequest,
    ): ResponseEntity<SessionResponse> {
        val view = training.startSession(
            StartSessionCommand(
                userId = user.userId,
                kind = request.kind!!,
                difficulty = request.difficulty!!,
                taskCount = request.taskCount!!,
            ),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(SessionResponse.of(view))
    }

    @Operation(summary = "Текущее состояние тренировки")
    @GetMapping("/sessions/{sessionId}")
    fun session(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable sessionId: String,
    ): SessionResponse = SessionResponse.of(training.getSession(user.userId, SessionId.parse(sessionId)))

    @Operation(
        summary = "Отправить ответ на задание",
        description = "Правильный ответ считает сервер. Повторная отправка того же номера задания " +
            "идемпотентна: вернётся ранее сохранённый результат с признаком duplicate.",
    )
    @PostMapping("/sessions/{sessionId}/answers")
    fun answer(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable sessionId: String,
        @Valid @RequestBody request: SubmitAnswerRequest,
    ): AnswerResponse = AnswerResponse.of(
        training.submitAnswer(
            SubmitAnswerCommand(
                userId = user.userId,
                sessionId = SessionId.parse(sessionId),
                taskIndex = request.taskIndex!!,
                answer = request.answer!!,
                elapsedMs = request.elapsedMs!!,
            ),
        ),
    )

    @Operation(summary = "Прервать тренировку")
    @PostMapping("/sessions/{sessionId}/abandon")
    fun abandon(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable sessionId: String,
    ): SessionResponse = SessionResponse.of(training.abandonSession(user.userId, SessionId.parse(sessionId)))

    @Operation(summary = "Разбор ответов тренировки")
    @GetMapping("/sessions/{sessionId}/answers")
    fun answers(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable sessionId: String,
    ): List<AnswerRecordResponse> =
        training.listAnswers(user.userId, SessionId.parse(sessionId)).map(AnswerRecordResponse::of)

    @Operation(summary = "История тренировок")
    @GetMapping("/sessions")
    fun history(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ): PageResponse<SessionSummaryResponse> =
        PageResponse.of(training.history(user.userId, PageRequest.of(page, size)), SessionSummaryResponse::of)
}
