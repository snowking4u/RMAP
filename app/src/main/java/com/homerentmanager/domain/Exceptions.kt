package com.homerentmanager.domain

class ValidationException(message: String) : IllegalArgumentException(message)

class ConflictException(message: String) : IllegalStateException(message)

class NotFoundException(message: String) : NoSuchElementException(message)
