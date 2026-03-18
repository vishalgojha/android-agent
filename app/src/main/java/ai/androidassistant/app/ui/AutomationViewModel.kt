package ai.androidassistant.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.androidassistant.app.automation.AutomationEngine
import ai.androidassistant.app.automation.AutomationRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for automation feature
 */
class AutomationViewModel(application: Application) : AndroidViewModel(application) {
  private val _rules = MutableStateFlow<List<AutomationRule>>(emptyList())
  val rules: StateFlow<List<AutomationRule>> = _rules.asStateFlow()

  init {
    AutomationEngine.initialize(application)
    _rules.value = AutomationEngine.getRules()

    viewModelScope.launch {
      AutomationEngine.getRulesFlow().collect { ruleList ->
        _rules.value = ruleList
      }
    }
  }

  fun createRule(rule: AutomationRule) {
    viewModelScope.launch {
      AutomationEngine.addRule(rule)
    }
  }

  fun deleteRule(ruleId: String) {
    viewModelScope.launch {
      AutomationEngine.removeRule(ruleId)
    }
  }

  fun toggleRule(ruleId: String) {
    viewModelScope.launch {
      val currentRule = _rules.value.find { it.id == ruleId } ?: return@launch
      if (currentRule.isEnabled) {
        AutomationEngine.disableRule(ruleId)
      } else {
        AutomationEngine.enableRule(ruleId)
      }
    }
  }

  fun updateRule(ruleId: String, updates: AutomationRule.() -> AutomationRule) {
    viewModelScope.launch {
      AutomationEngine.updateRule(ruleId, updates)
    }
  }

  fun getRuleCount(): Int = _rules.value.size

  fun getEnabledRuleCount(): Int = _rules.value.count { it.isEnabled }
}

