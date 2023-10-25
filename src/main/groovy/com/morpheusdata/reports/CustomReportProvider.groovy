package com.morpheusdata.reports

import com.morpheusdata.core.AbstractReportProvider
import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.model.OptionType
import com.morpheusdata.model.ReportResult
import com.morpheusdata.model.ReportType
import com.morpheusdata.model.ReportResultRow
import com.morpheusdata.model.ContentSecurityPolicy
import com.morpheusdata.views.HTMLResponse
import com.morpheusdata.views.ViewModel
import com.morpheusdata.response.ServiceResponse
import groovy.sql.GroovyRowResult
import groovy.sql.Sql
import groovy.util.logging.Slf4j
import io.reactivex.Observable;
import java.util.Date

import java.sql.Connection

@Slf4j
class CustomReportProvider extends AbstractReportProvider {
  Plugin plugin
  MorpheusContext morpheusContext

  CustomReportProvider(Plugin plugin, MorpheusContext context) {
    this.plugin = plugin
    this.morpheusContext = context
  }

  @Override
  MorpheusContext getMorpheus() {
    morpheusContext
  }

  @Override
  Plugin getPlugin() {
    plugin
  }

  // Define the Morpheus code associated with the plugin
  @Override
  String getCode() {
    'instance-summary-date-created'
  }

  // Define the name of the report displayed on the reports page
  @Override
  String getName() {
    'Instance Summary (Ordered by date created)'
  }

   ServiceResponse validateOptions(Map opts) {
     return ServiceResponse.success()
   }

  @Override
  HTMLResponse renderTemplate(ReportResult reportResult, Map<String, List<ReportResultRow>> reportRowsBySection) {
    ViewModel<String> model = new ViewModel<String>()
    model.object = reportRowsBySection
    getRenderer().renderTemplate("hbs/instanceReport", model)
  }

  	@Override
	ContentSecurityPolicy getContentSecurityPolicy() {
		def csp = new ContentSecurityPolicy()
		csp
	}

  void process(ReportResult reportResult) {
    // Update the status of the report (generating) - https://developer.morpheusdata.com/api/com/morpheusdata/model/ReportResult.Status.html
    morpheus.report.updateReportResultStatus(reportResult,ReportResult.Status.generating).blockingGet();
    Long displayOrder = 0
    List<GroovyRowResult> results = []
    Connection dbConnection
    Long totalItems = 0

    try {
      // Create a read-only database connection
      dbConnection = morpheus.report.getReadOnlyDatabaseConnection().blockingGet()
      // Evaluate if a search filter or phrase has been defined
        results = new Sql(dbConnection).rows("SELECT id, name, date_created FROM instance order by date_created desc;")
      // Close the database connection
    } finally {
      morpheus.report.releaseDatabaseConnection(dbConnection)
    }
    log.info("Results: ${results}")
    Observable<GroovyRowResult> observable = Observable.fromIterable(results) as Observable<GroovyRowResult>
    observable.map{ resultRow ->
      log.info("Mapping resultRow ${resultRow}")
      Map<String,Object> data = [id: resultRow.id, name: resultRow.name.toString(), date_created: resultRow.date_created.toString() ]
      ReportResultRow resultRowRecord = new ReportResultRow(section: ReportResultRow.SECTION_MAIN, displayOrder: displayOrder++, dataMap: data)
      log.info("resultRowRecord: ${resultRowRecord.dump()}")
      totalItems++
      return resultRowRecord
    }.buffer(50).doOnComplete {
      morpheus.report.updateReportResultStatus(reportResult,ReportResult.Status.ready).blockingGet();
    }.doOnError { Throwable t ->
      morpheus.report.updateReportResultStatus(reportResult,ReportResult.Status.failed).blockingGet();
    }.subscribe {resultRows ->
      morpheus.report.appendResultRows(reportResult,resultRows).blockingGet()
    }
    Map<String,Object> data = [total_items: totalItems]
    ReportResultRow resultRowRecord = new ReportResultRow(section: ReportResultRow.SECTION_HEADER, displayOrder: displayOrder++, dataMap: data)
        morpheus.report.appendResultRows(reportResult,[resultRowRecord]).blockingGet()
  }

  // https://developer.morpheusdata.com/api/com/morpheusdata/core/ReportProvider.html#method.summary
  // The description associated with the custom report
   @Override
   String getDescription() {
     return "View an inventory of instances ordered by date created"
   }

   // The category of the custom report
   @Override
   String getCategory() {
     return 'inventory'
   }

   @Override
   Boolean getOwnerOnly() {
     return false
   }
   
   @Override
   List<OptionType> getOptionTypes() {
    return 
   }

   @Override
   Boolean getMasterOnly() {
     return true
   }

   @Override
   Boolean getSupportsAllZoneTypes() {
     return true
   }
  }