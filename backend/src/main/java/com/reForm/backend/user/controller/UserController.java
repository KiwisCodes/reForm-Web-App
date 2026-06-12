package com.reForm.backend.user.controller;


import com.reForm.backend.user.port.IUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping()
@RequiredArgsConstructor
@CrossOrigin
@Slf4j
@Validated
public class UserController {
    private final IUserService userService;





}
