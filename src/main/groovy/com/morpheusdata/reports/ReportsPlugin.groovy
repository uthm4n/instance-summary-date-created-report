package com.morpheusdata.reports

import com.morpheusdata.core.Plugin

class ReportsPlugin extends Plugin {

  String getCode() {
    'instance-summary-date-created'
  }

  @Override
  void initialize() {
    CustomReportProvider customReportProvider = new CustomReportProvider(this, morpheus)
    this.pluginProviders.put(customReportProvider.code, customReportProvider)
    this.setName("Instance Report (Date Created DESC)")
    this.setDescription("A custom report plugin for instances ordered by date created")
  }

  @Override
  void onDestroy() {
  }
}