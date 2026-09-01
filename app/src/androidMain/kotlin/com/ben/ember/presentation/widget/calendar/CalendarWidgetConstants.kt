// The saved-state keys and intent actions this widget uses to remember and change the shown month.
package com.ben.ember.presentation.widget.calendar

import androidx.datastore.preferences.core.stringPreferencesKey

val shownMonthKey = stringPreferencesKey("shown_month")
val cachedCalendarKey = stringPreferencesKey("cached_calendar")

const val showPreviousMonthAction = "com.ben.ember.widget.calendar.SHOW_PREVIOUS_MONTH"
const val showNextMonthAction = "com.ben.ember.widget.calendar.SHOW_NEXT_MONTH"
