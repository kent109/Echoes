package com.gemform.be.echoes.controller;

import java.util.List;

import com.gemform.be.echoes.entity.Echo;
import com.gemform.be.echoes.repository.EchoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/echoes")
public class EchoController {

    private final EchoRepository echoRepository;

    public EchoController(EchoRepository echoRepository) {
        this.echoRepository = echoRepository;
    }

    public record EchoRequest(String content) {
    }

    @GetMapping("/getAll")
    public List<Echo> list() {
        return echoRepository.findAll();
    }

    @PostMapping("/save")
    public Echo create(@RequestBody EchoRequest request) {
        if (request.content() == null || request.content().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content must not be blank");
        }
        Echo echo = new Echo();
        echo.setContent(request.content());
        return echoRepository.save(echo);
    }
}
