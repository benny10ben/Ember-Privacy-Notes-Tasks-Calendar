package com.ben.ember.domain.util

import com.ben.ember.domain.model.ParsedTask

interface TaskExtractor {
    fun extractTasks(transcript: String): List<ParsedTask>
}