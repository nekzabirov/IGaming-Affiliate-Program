package io.github.nekzabirov.affiliate.rest

import io.github.nekzabirov.affiliate.model.Account
import io.github.nekzabirov.affiliate.model.Postback
import io.github.nekzabirov.affiliate.services.PostbackService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** A company's postbacks; the company must be the signed-in partner's own. */
@RestController
@RequestMapping("/api/companies/{identity}/postbacks")
class PostbackController(private val postbacks: PostbackService) {

    @GetMapping
    fun list(@RequestAttribute(SessionFilter.ACCOUNT) account: Account, @PathVariable identity: String): List<PostbackResponse> =
        postbacks.list(account, identity).map(::respond)

    @PostMapping
    fun create(
        @RequestAttribute(SessionFilter.ACCOUNT) account: Account,
        @PathVariable identity: String,
        @RequestBody body: CreatePostbackRequest,
    ): ResponseEntity<PostbackResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(respond(postbacks.create(account, identity, body.type, body.url)))

    @PatchMapping("/{id}")
    fun update(
        @RequestAttribute(SessionFilter.ACCOUNT) account: Account,
        @PathVariable identity: String,
        @PathVariable id: Long,
        @RequestBody body: UpdatePostbackRequest,
    ): PostbackResponse = respond(postbacks.update(account, identity, id, body.active, body.url))

    @DeleteMapping("/{id}")
    fun delete(
        @RequestAttribute(SessionFilter.ACCOUNT) account: Account,
        @PathVariable identity: String,
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        postbacks.delete(account, identity, id)
        return ResponseEntity.noContent().build()
    }

    private fun respond(postback: Postback) = PostbackResponse.of(postback, postbacks.examples(postback))
}
