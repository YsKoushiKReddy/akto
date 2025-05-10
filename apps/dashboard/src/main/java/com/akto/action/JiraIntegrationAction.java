package com.akto.action;

import static com.akto.utils.Utils.createRequestFile;
import static com.akto.utils.Utils.getTestResultFromTestingRunResult;

import com.akto.dao.ConfigsDao;
import com.akto.dao.JiraIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing.BidirectionalSyncSettingsDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.Config.AktoHostUrlConfig;
import com.akto.dto.Config.ConfigType;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.jira_integration.BiDirectionalSyncSettings;
import com.akto.dto.jira_integration.JiraIntegration;
import com.akto.dto.jira_integration.JiraMetaData;
import com.akto.dto.jira_integration.JiraStatus;
import com.akto.dto.jira_integration.JiraStatusApiResponse;
import com.akto.dto.jira_integration.ProjectMapping;
import com.akto.dto.jobs.Job;
import com.akto.dto.jobs.JobExecutorType;
import com.akto.dto.jobs.TicketSyncJobParams;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.BidirectionalSyncSettings;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunResult;
import com.akto.jobs.JobScheduler;
import com.akto.jobs.utils.JobConstants;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.test_editor.Utils;
import com.akto.testing.ApiExecutor;
import com.akto.util.Constants;
import com.akto.util.DashboardMode;
import com.akto.util.enums.GlobalEnums;
import com.akto.util.enums.GlobalEnums.TicketSource;
import com.akto.util.http_util.CoreHTTPClient;
import com.akto.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.struts2.interceptor.ServletRequestAware;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

public class JiraIntegrationAction extends UserAction implements ServletRequestAware {

    private String baseUrl;
    private String projId;
    private String userEmail;
    private String apiToken;
    private String issueType;
    private JiraIntegration jiraIntegration;
    private JiraMetaData jiraMetaData;

    private String jiraTicketKey;

    private String origReq;
    private String testReq;
    private String issueId;

    private String dashboardUrl;

    private Map<String,List<BasicDBObject>> projectAndIssueMap;
    private Map<String, ProjectMapping> projectMappings;

    private static final String META_ENDPOINT = "/rest/api/3/issue/createmeta";
    private static final String CREATE_ISSUE_ENDPOINT = "/rest/api/3/issue";
    private static final String CREATE_ISSUE_ENDPOINT_BULK = "/rest/api/3/issue/bulk";
    private static final String ATTACH_FILE_ENDPOINT = "/attachments";
    private static final String ISSUE_STATUS_ENDPOINT = "/rest/api/3/project/%s/statuses";
    private static final LoggerMaker loggerMaker = new LoggerMaker(ApiExecutor.class, LogDb.DASHBOARD);

    private static final int TICKET_SYNC_JOB_RECURRING_INTERVAL_SECONDS = 3600;
    private static final OkHttpClient client = CoreHTTPClient.client.newBuilder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    public String testIntegration() {

        String url = baseUrl + META_ENDPOINT;
        if(apiToken.contains("******")){
            JiraIntegration jiraIntegration = JiraIntegrationDao.instance.findOne(Filters.empty());
            if(jiraIntegration != null){
                setApiToken(jiraIntegration.getApiToken());
            }
        }
        String authHeader = Base64.getEncoder().encodeToString((userEmail + ":" + apiToken).getBytes());
        try {

            Request.Builder builder = new Request.Builder();
            builder.addHeader("Authorization", "Basic " + authHeader);
            builder.addHeader("Accept-Encoding", "gzip");
            builder = builder.url(url);
            Request okHttpRequest = builder.build();
            Call call = client.newCall(okHttpRequest);
            Response response = null;
            String responsePayload = null;
            try {
                response = call.execute();
                responsePayload = response.body().string();
                if (responsePayload == null) {
                    addActionError("Error while testing jira integration, received null response");
                    loggerMaker.errorAndAddToDb("error while testing jira integration, received null response", LoggerMaker.LogDb.DASHBOARD);
                    return Action.ERROR.toUpperCase();
                }
                if (!Utils.isJsonPayload(responsePayload)) {
                    builder.removeHeader("Accept-Encoding");
                    builder = builder.url(url);
                    okHttpRequest = builder.build();
                    call = client.newCall(okHttpRequest);
                    response = call.execute();
                    responsePayload = response.body().string();
                    if (responsePayload == null) {
                        addActionError("Error while testing jira integration, received null response");
                        loggerMaker.errorAndAddToDb("error while testing jira integration, received null response", LoggerMaker.LogDb.DASHBOARD);
                        return Action.ERROR.toUpperCase();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                addActionError("Error while testing jira integration, error making call\"");
                loggerMaker.errorAndAddToDb("error while testing jira integration, error making call" + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
                return Action.ERROR.toUpperCase();
            } finally {
                if (response != null) {
                    response.close();
                }
            }
            BasicDBObject payloadObj;
            setProjId(projId.replaceAll("\\s+", ""));
            Set<String> inputProjectIds = new HashSet(Arrays.asList(this.projId.split(",")));
            this.projectAndIssueMap = new HashMap<>();
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBList projects = (BasicDBList) payloadObj.get("projects");
                for (Object projObj: projects) {
                    BasicDBObject obj = (BasicDBObject) projObj;
                    String key = obj.getString("key");
                    if (!inputProjectIds.contains(key)) {
                        continue;
                    }
                    loggerMaker.debugAndAddToDb("evaluating issuetype for project key " + key + ", project json obj " + obj, LoggerMaker.LogDb.DASHBOARD);
                    BasicDBList issueTypes = (BasicDBList) obj.get("issuetypes");
                    List<BasicDBObject> issueIdPairs = getIssueTypesWithIds(issueTypes);
                    this.projectAndIssueMap.put(key, issueIdPairs);
                }
                if (this.projectAndIssueMap.isEmpty()) {
                    addActionError("Error while testing jira integration, unable to resolve issue type id");
                    loggerMaker.errorAndAddToDb("Error while testing jira integration, unable to resolve issue type id", LoggerMaker.LogDb.DASHBOARD);
                    return Action.ERROR.toUpperCase();
                }
            } catch(Exception e) {
                return Action.ERROR.toUpperCase();
            }
        } catch (Exception e) {
            addActionError("Error while testing jira integration");
            loggerMaker.errorAndAddToDb("error while testing jira integration, " + e, LoggerMaker.LogDb.DASHBOARD);
            return Action.ERROR.toUpperCase();
        }

        return Action.SUCCESS.toUpperCase();
    }

    public String fetchJiraStatusMappings() {
        if(apiToken.contains("******")){
            JiraIntegration jiraIntegration = JiraIntegrationDao.instance.findOne(Filters.empty());
            if(jiraIntegration != null){
                setApiToken(jiraIntegration.getApiToken());
            }
        }

        String authHeader = Base64.getEncoder().encodeToString((userEmail + ":" + apiToken).getBytes());

        try {
            setProjId(projId.replaceAll("\\s+", ""));

            // Step 2: Call the API to get issue types for the project
            String statusUrl = baseUrl + String.format(ISSUE_STATUS_ENDPOINT, projId) + "?maxResults=100";

            Request.Builder builder = new Request.Builder();
            builder.addHeader("Authorization", "Basic " + authHeader);
            builder.addHeader("Accept", "application/json");
            builder = builder.url(statusUrl);
            Request okHttpRequest = builder.build();

            Response response = null;
            String responsePayload;

            try {
                response = client.newCall(okHttpRequest).execute();

                if (!response.isSuccessful()) {
                    loggerMaker.error(
                        "Error while fetching Jira Project statuses. Response code: {}, accountId: {}, projectId: {}",
                        response.code(), Context.accountId.get(), projId);
                    return Action.ERROR.toUpperCase();
                }

                if (response.body() == null) {
                    loggerMaker.error(
                        "Error while fetching Jira Project statuses. Response body is null. accountId: {}, projectId: {}",
                        Context.accountId.get(), projId);
                    return Action.ERROR.toUpperCase();
                }

                responsePayload = response.body().string();

            } catch (Exception e) {
                String msg = "Error while fetching Jira Project statuses";
                addActionError(msg);
                loggerMaker.error(msg, e);
                return Action.ERROR.toUpperCase();
            } finally {
                if (response != null) {
                    response.close();
                }
            }

            this.projectMappings = new HashMap<>();
            this.projectMappings.put(projId, new ProjectMapping(
                extractUniqueStatusCategories(responsePayload),
                null
            ));
            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            addActionError("Error while fetching jira project status mappings");
            loggerMaker.error("Error while fetching jira project status mappings. p[rojId: {}", projId, e);
            return Action.ERROR.toUpperCase();
        }
    }

    private Set<JiraStatus> extractUniqueStatusCategories(String responsePayload) {
        List<JiraStatusApiResponse> statusesMap = JsonUtils.fromJson(responsePayload,
            new TypeReference<List<JiraStatusApiResponse>>() {
            });

        if (statusesMap == null || statusesMap.isEmpty()) {
            return Collections.emptySet();
        }

        return statusesMap.stream()
            .flatMap(statusResp -> statusResp.getStatuses().stream())
            .collect(Collectors.toSet());
    }

    private List<BasicDBObject> getIssueTypesWithIds(BasicDBList issueTypes) {

        List<BasicDBObject> idPairs = new ArrayList<>();
        for (Object issueObj: issueTypes) {
            BasicDBObject obj2 = (BasicDBObject) issueObj;
            String issueName = obj2.getString("name");
            String issueId = obj2.getString("id");
            BasicDBObject finalObj = new BasicDBObject();
            finalObj.put("issueId", issueId);
            finalObj.put("issueType", issueName);
            idPairs.add(finalObj);
        }
        return idPairs;
    }

    public String addIntegration() {

        addAktoHostUrl();

        UpdateOptions updateOptions = new UpdateOptions();
        updateOptions.upsert(true);
        Bson tokenUpdate = Updates.set("apiToken", apiToken);
        Bson integrationUpdate = Updates.combine(
            Updates.set("baseUrl", baseUrl),
            Updates.set("projId", projId),
            Updates.set("userEmail", userEmail),
            Updates.set("issueType", issueType),
            Updates.setOnInsert("createdTs", Context.now()),
            Updates.set("updatedTs", Context.now()),
            Updates.set("projectIdsMap", projectAndIssueMap),
            Updates.set("projectMappings", projectMappings)
        );
        if(!apiToken.contains("******")){
            integrationUpdate = Updates.combine(integrationUpdate, tokenUpdate);
        }

        JiraIntegrationDao.instance.getMCollection().updateOne(
                new BasicDBObject(),
                integrationUpdate,
                updateOptions
        );

        return Action.SUCCESS.toUpperCase();
    }

    public String addIntegrationV2() {

        addAktoHostUrl();

        if (projectMappings == null || projectMappings.isEmpty()) {
            addActionError("Project mappings cannot be empty");
            return Action.ERROR.toUpperCase();
        }

        this.projectAndIssueMap = new HashMap<>();
        try {
            for (Map.Entry<String, ProjectMapping> entry : projectMappings.entrySet()) {
                List<BasicDBObject> issueTypes = getProjectMetadata(entry.getKey());
                this.projectAndIssueMap.put(entry.getKey(), issueTypes);
            }
        } catch (Exception ex) {
            loggerMaker.error("Error while fetching project metadata", ex);
            addActionError("Error while fetching project metadata");
            return Action.ERROR.toUpperCase();
        }

        JiraIntegration existingIntegration = JiraIntegrationDao.instance.findOne(new BasicDBObject());

        if (existingIntegration == null) {
            String response = addIntegration();
            this.jiraIntegration = JiraIntegrationDao.instance.findOne(new BasicDBObject());
            syncBidirectionalSettings(jiraIntegration);
            return response;
        }

        Map<String, ProjectMapping> existingProjectMappings = existingIntegration.getProjectMappings();

        if (existingProjectMappings == null) {
            existingProjectMappings = new HashMap<>();
        }

        for (Map.Entry<String, ProjectMapping> entry : projectMappings.entrySet()) {
            ProjectMapping mapping = entry.getValue();
            if (!mapping.getBiDirectionalSyncSettings().isEnabled()) {
                mapping.setStatuses(null);
            }
        }

        existingProjectMappings.putAll(projectMappings);

        UpdateOptions updateOptions = new UpdateOptions();
        updateOptions.upsert(false);

        Bson integrationUpdate = Updates.combine(
            Updates.set("updatedTs", Context.now()),
            Updates.set("projectMappings", existingProjectMappings)
        );

        Map<String, List<BasicDBObject>> existingProjectIdMap = existingIntegration.getProjectIdsMap();

        if (existingProjectIdMap == null) {
            existingProjectIdMap = new HashMap<>();
        }

        existingProjectIdMap.putAll(this.projectAndIssueMap);

        integrationUpdate = Updates.combine(integrationUpdate, Updates.set("projectIdsMap", existingProjectIdMap));

        JiraIntegrationDao.instance.getMCollection().updateOne(
            new BasicDBObject(),
            integrationUpdate,
            updateOptions
        );

        this.jiraIntegration = JiraIntegrationDao.instance.findOne(new BasicDBObject());

        syncBidirectionalSettings(jiraIntegration);

        return Action.SUCCESS.toUpperCase();
    }

    public String deleteProject() {
        if (this.projId == null || this.projId.isEmpty()) {
            addActionError("Project Id cannot be null");
            return Action.ERROR.toUpperCase();
        }

        JiraIntegration jira = JiraIntegrationDao.instance.findOne(new BasicDBObject());
        if (jira == null) {
            addActionError("Jira Integration not found. AccountId: " + Context.accountId.get());
            return Action.ERROR.toUpperCase();
        }

        Map<String, List<BasicDBObject>> existingProjectIdsMap = jira.getProjectIdsMap();
        Map<String, ProjectMapping> existingProjectMappings = jira.getProjectMappings();

        if (existingProjectIdsMap.isEmpty() || (existingProjectMappings != null && existingProjectMappings.isEmpty())) {
            addActionError("Atleast one project is required for Jira Integration");
            return Action.ERROR.toUpperCase();
        }

        List<BasicDBObject> removedProjectIdMap = existingProjectIdsMap.remove(this.projId);

        // null check for backward compatibility - users who already have jira integration before this change.
        ProjectMapping removedProjectMapping = existingProjectMappings == null ? null : existingProjectMappings.remove(this.projId);

        if (removedProjectIdMap == null && removedProjectMapping == null) {
            loggerMaker.debug("Project Key not found in Jira Integration: projId: {}", this.projId);
            return Action.SUCCESS.toUpperCase();
        }

        UpdateOptions updateOptions = new UpdateOptions();
        updateOptions.upsert(false);

        Bson integrationUpdate = Updates.combine(
            Updates.set("updatedTs", Context.now()),
            Updates.set("projectIdsMap", existingProjectIdsMap)
        );

        if (existingProjectMappings != null) {
            integrationUpdate = Updates.combine(integrationUpdate, Updates.set("projectMappings", existingProjectMappings));
        }

        JiraIntegrationDao.instance.getMCollection().updateOne(
            new BasicDBObject(),
            integrationUpdate,
            updateOptions
        );

        BidirectionalSyncSettings disabledSettings = BidirectionalSyncSettingsDao.instance.getMCollection()
            .findOneAndUpdate(
                Filters.and(
                    Filters.eq(BidirectionalSyncSettings.SOURCE, TicketSource.JIRA.name()),
                    Filters.eq(BidirectionalSyncSettings.PROJECT_KEY, this.projId)
                ),
                Updates.combine(
                    Updates.set(BidirectionalSyncSettings.ACTIVE, false),
                    Updates.set(BidirectionalSyncSettings.LAST_SYNCED_AT, Context.now())
                ),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
            );
        if (disabledSettings != null) {
            JobScheduler.deleteJob(disabledSettings.getJobId());
        }
        return Action.SUCCESS.toUpperCase();
    }

    private List<BasicDBObject> getProjectMetadata(String projectId) throws Exception {

        if (apiToken.contains("******")) {
            JiraIntegration jiraIntegration = JiraIntegrationDao.instance.findOne(Filters.empty());
            if (jiraIntegration != null) {
                setApiToken(jiraIntegration.getApiToken());
            } else {
                throw new IllegalStateException("No Jira integration found");
            }
        }

        String url = baseUrl + META_ENDPOINT;
        String authHeader = Base64.getEncoder().encodeToString((userEmail + ":" + apiToken).getBytes());

        Request.Builder builder = new Request.Builder();
        builder.addHeader("Authorization", "Basic " + authHeader);
        builder.addHeader("Accept-Encoding", "gzip");
        builder = builder.url(url);
        Request okHttpRequest = builder.build();

        Response response = null;
        String responsePayload;

        try {
            response = client.newCall(okHttpRequest).execute();
            responsePayload = response.body().string();

            if (!Utils.isJsonPayload(responsePayload)) {
                builder.removeHeader("Accept-Encoding");
                builder = builder.url(url);
                okHttpRequest = builder.build();
                response = client.newCall(okHttpRequest).execute();
                responsePayload = response.body().string();
            }
        } finally {
            if (response != null) {
                response.close();
            }
        }

        BasicDBObject payloadObj = BasicDBObject.parse(responsePayload);
        BasicDBList projects = (BasicDBList) payloadObj.get("projects");

        for (Object projObj : projects) {
            BasicDBObject obj = (BasicDBObject) projObj;
            String key = obj.getString("key");

            if (projectId.equals(key)) {
                BasicDBList issueTypes = (BasicDBList) obj.get("issuetypes");
                return getIssueTypesWithIds(issueTypes);
            }
        }

        throw new IllegalArgumentException("Project with ID '" + projectId + "' not found");
    }

    public String fetchIntegration() {

        addAktoHostUrl();

        jiraIntegration = JiraIntegrationDao.instance.findOne(new BasicDBObject());
        if(jiraIntegration != null){
            jiraIntegration.setApiToken("****************************");
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String createIssue() {

        BasicDBObject reqPayload = new BasicDBObject();
        jiraIntegration = JiraIntegrationDao.instance.findOne(new BasicDBObject());
        if(jiraIntegration == null) {
            addActionError("Jira is not integrated.");
            return ERROR.toUpperCase();
        }
        BasicDBObject fields = jiraTicketPayloadCreator(jiraMetaData);

        reqPayload.put("fields", fields);

        String url = jiraIntegration.getBaseUrl() + CREATE_ISSUE_ENDPOINT;
        String authHeader = Base64.getEncoder().encodeToString((jiraIntegration.getUserEmail() + ":" + jiraIntegration.getApiToken()).getBytes());

        String jiraTicketUrl = "";
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Basic " + authHeader));
        OriginalHttpRequest request = new OriginalHttpRequest(url, "", "POST", reqPayload.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());
            String responsePayload = response.getBody();
            if (response.getStatusCode() > 201 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("error while creating jira issue, url not accessible, requestbody " + request.getBody() + " ,responsebody " + response.getBody() + " ,responsestatus " + response.getStatusCode(), LoggerMaker.LogDb.DASHBOARD);
                if (responsePayload != null) {
                    try {
                        BasicDBObject obj = BasicDBObject.parse(responsePayload);
                        List<String> errorMessages = (List) obj.get("errorMessages");
                        String error;
                        if (errorMessages.size() == 0) {
                            BasicDBObject errObj = BasicDBObject.parse(obj.getString("errors"));
                            error = errObj.getString("project");
                        } else {
                            error = errorMessages.get(0);
                        }
                        addActionError(error);
                    } catch (Exception e) {
                        // TODO: handle exception
                    }
                }
                return Action.ERROR.toUpperCase();
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                this.jiraTicketKey = payloadObj.getString("key");
                jiraTicketUrl = jiraIntegration.getBaseUrl() + "/browse/" + this.jiraTicketKey;
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb(e, "error making jira issue url " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
                return Action.ERROR.toUpperCase();
            }
        } catch(Exception e) {
            return Action.ERROR.toUpperCase();
        }

        UpdateOptions updateOptions = new UpdateOptions();
        updateOptions.upsert(false);

        if(jiraTicketUrl.length() > 0){
            TestingRunIssuesDao.instance.getMCollection().updateOne(
                Filters.eq(Constants.ID, jiraMetaData.getTestingIssueId()),
                Updates.combine(
                    Updates.set("jiraIssueUrl", jiraTicketUrl),
                    Updates.set(TestingRunIssues.TICKET_SOURCE, GlobalEnums.TicketSource.JIRA.name()),
                    Updates.set(TestingRunIssues.TICKET_PROJECT_KEY, projId),
                    Updates.set(TestingRunIssues.TICKET_ID, this.jiraTicketKey),
                    Updates.set(TestingRunIssues.TICKET_LAST_UPDATED_AT, Context.now())
                ),
                updateOptions
            );
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String attachFileToIssue() {

        try {
            jiraIntegration = JiraIntegrationDao.instance.findOne(new BasicDBObject());
            String url = jiraIntegration.getBaseUrl() + CREATE_ISSUE_ENDPOINT + "/" + issueId + ATTACH_FILE_ENDPOINT;
            String authHeader = Base64.getEncoder().encodeToString((jiraIntegration.getUserEmail() + ":" + jiraIntegration.getApiToken()).getBytes());

            File tmpOutputFile = createRequestFile(origReq, testReq);
            if(tmpOutputFile == null) {
                return Action.SUCCESS.toUpperCase();
            }

            MediaType mType = MediaType.parse("application/octet-stream");
            RequestBody requestBody = new MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("file", tmpOutputFile.getName(),
                            RequestBody.create(tmpOutputFile, mType))
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .header("Authorization", "Basic " + authHeader)
                    .header("X-Atlassian-Token", "nocheck")
                    .build();

            Response response = null;

            try {
                response = client.newCall(request).execute();
            } catch (Exception ex) {
                loggerMaker.errorAndAddToDb(ex,
                        String.format("Failed to call jira from url %s. Error %s", url, ex.getMessage()),
                        LogDb.DASHBOARD);
            } finally {
                if (response != null) {
                    response.close();
                }
            }

        } catch (Exception ex) {
                ex.printStackTrace();
        }


        return Action.SUCCESS.toUpperCase();
    }

    private BasicDBObject buildContentDetails(String txt, String link) {
        BasicDBObject details = new BasicDBObject();
        details.put("type", "paragraph");
        BasicDBList contentInnerList = new BasicDBList();
        BasicDBObject innerDetails = new BasicDBObject();
        innerDetails.put("text", txt);
        innerDetails.put("type", "text");

        if (link != null) {
            BasicDBList marksList = new BasicDBList();
            BasicDBObject marks = new BasicDBObject();
            marks.put("type", "link");
            BasicDBObject attrs = new BasicDBObject();
            attrs.put("href", link);
            marks.put("attrs", attrs);
            marksList.add(marks);
            innerDetails.put("marks", marksList);
        }

        contentInnerList.add(innerDetails);
        details.put("content", contentInnerList);


        return details;
    }

    String aktoDashboardHost;
    List<TestingIssuesId> issuesIds;
    private String errorMessage;

    public String bulkCreateJiraTickets (){
        if(issuesIds == null || issuesIds.isEmpty()){
            addActionError("Cannot create an empty jira issue.");
            return ERROR.toUpperCase();
        }

        if((projId == null || projId.isEmpty()) || (issueType == null || issueType.isEmpty())){
            addActionError("Project ID or Issue Type cannot be empty.");
            return ERROR.toUpperCase();
        }

        jiraIntegration = JiraIntegrationDao.instance.findOne(new BasicDBObject());
        if(jiraIntegration == null) {
            addActionError("Jira is not integrated.");
            return ERROR.toUpperCase();
        }

        List<JiraMetaData> jiraMetaDataList = new ArrayList<>();
        Bson projection = Projections.include(YamlTemplate.INFO);
        List<String> testingSubCategories = new ArrayList<>();
        for(TestingIssuesId testingIssuesId : issuesIds) {
            testingSubCategories.add(testingIssuesId.getTestSubCategory());
        }
        List<YamlTemplate> yamlTemplateList = YamlTemplateDao.instance.findAll(Filters.in("_id", testingSubCategories), projection);
        Map<String, Info> testSubTypeToInfoMap = new HashMap<>();
        for(YamlTemplate yamlTemplate : yamlTemplateList) {
            if(yamlTemplate == null || yamlTemplate.getInfo() == null) {
                loggerMaker.errorAndAddToDb("ERROR: YamlTemplate or YamlTemplate.info is null", LogDb.DASHBOARD);
                continue;
            }
            Info info = yamlTemplate.getInfo();
            testSubTypeToInfoMap.put(info.getSubCategory(), info);
        }

        List<TestingRunResult> testingRunResultList = new ArrayList<>();
        int existingIssues = 0;
        List<TestingRunIssues> testingRunIssuesList = TestingRunIssuesDao.instance.findAll(Filters.and(
                Filters.in("_id", issuesIds),
                Filters.exists("jiraIssueUrl", true)
        ));
        Set<TestingIssuesId> testingRunIssueIds = new HashSet<>();
        for (TestingRunIssues testingRunIssues : testingRunIssuesList) {
            testingRunIssueIds.add(testingRunIssues.getId());
        }

        for(TestingIssuesId testingIssuesId : issuesIds) {
            if(testingRunIssueIds.contains(testingIssuesId)) {
                existingIssues++;
                continue;
            }

            Info info = testSubTypeToInfoMap.get(testingIssuesId.getTestSubCategory());

            TestingRunResult testingRunResult = TestingRunResultDao.instance.findOne(Filters.and(
                    Filters.in(TestingRunResult.TEST_SUB_TYPE, testingIssuesId.getTestSubCategory()),
                    Filters.in(TestingRunResult.API_INFO_KEY, testingIssuesId.getApiInfoKey())
            ));

            if(testingRunResult == null) {
                loggerMaker.errorAndAddToDb("Error: Testing Run Result not found", LogDb.DASHBOARD);
                continue;
            }

            testingRunResultList.add(testingRunResult);

            JiraMetaData jiraMetaData;
            try {
                String inputUrl = testingIssuesId.getApiInfoKey().getUrl();

                URL url = new URL(inputUrl);
                String hostname = url.getHost();
                String endpoint = url.getPath();

                jiraMetaData = new JiraMetaData(
                        info.getName(),
                        "Host - "+hostname,
                        endpoint,
                        aktoDashboardHost+"/dashboard/issues?result="+testingRunResult.getId().toHexString(),
                        info.getDescription(),
                        testingIssuesId,
                        null
                );

            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error while parsing the url: " + e.getMessage(), LogDb.DASHBOARD);
                continue;
            }

            jiraMetaDataList.add(jiraMetaData);
        }

        BasicDBObject reqPayload = new BasicDBObject();
        BasicDBList issueUpdates = new BasicDBList();

        if(existingIssues == issuesIds.size()) {
            errorMessage = "All selected issues already have existing Jira tickets. No new tickets were created.";
        } else if(existingIssues > 0) {
            errorMessage = "Jira tickets created for all selected issues, except for " + existingIssues + " issues that already have tickets.";
        }

        if(jiraMetaDataList.isEmpty()) {
            return Action.SUCCESS.toUpperCase();
        }

        for (JiraMetaData jiraMetaData : jiraMetaDataList) {
            BasicDBObject fields = jiraTicketPayloadCreator(jiraMetaData);

            // Prepare the issue object
            BasicDBObject issueObject = new BasicDBObject();
            issueObject.put("fields", fields);
            issueUpdates.add(issueObject);
        }

        // Prepare the full request payload
        reqPayload.put("issueUpdates", issueUpdates);

        // URL for bulk create issues
        String url = jiraIntegration.getBaseUrl() + CREATE_ISSUE_ENDPOINT_BULK;
        String authHeader = Base64.getEncoder().encodeToString((jiraIntegration.getUserEmail() + ":" + jiraIntegration.getApiToken()).getBytes());

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Basic " + authHeader));

        OriginalHttpRequest request = new OriginalHttpRequest(url, "", "POST", reqPayload.toString(), headers, "");

        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());
            String responsePayload = response.getBody();

            if (response.getStatusCode() > 201 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("Error while creating Jira issues in bulk, URL not accessible, request body "
                                + request.getBody() + " ,response body " + response.getBody() + " ,response status " + response.getStatusCode(),
                        LoggerMaker.LogDb.DASHBOARD);

                // TODO: This is a duplicated function remove it.
                if (responsePayload != null) {
                    try {
                        BasicDBObject obj = BasicDBObject.parse(responsePayload);
                        List<String> errorMessages = (List) obj.get("errorMessages");
                        String error;
                        if (errorMessages.size() == 0) {
                            BasicDBObject errObj = BasicDBObject.parse(obj.getString("errors"));
                            error = errObj.getString("project");
                        } else {
                            error = errorMessages.get(0);
                        }
                        addActionError(error);
                    } catch (Exception e) {
                        // Handle exception
                    }
                }

                return Action.ERROR.toUpperCase();
            }

            BasicDBObject payloadObj;
            try {
                payloadObj = BasicDBObject.parse(responsePayload);
                List<BasicDBObject> issues = (List<BasicDBObject>) payloadObj.get("issues");
                for (int i = 0; i < Math.min(issues.size(), jiraMetaDataList.size()); i++) {
                    BasicDBObject issue = issues.get(i);
                    String issueKey = issue.getString("key");
                    String jiraTicketUrl = jiraIntegration.getBaseUrl() + "/browse/" + issueKey;

                    JiraMetaData metaData = jiraMetaDataList.get(i);
                    TestingRunIssuesDao.instance.getMCollection().updateOne(
                            Filters.eq(Constants.ID, metaData.getTestingIssueId()),
                            Updates.combine(
                                Updates.set("jiraIssueUrl", jiraTicketUrl),
                                Updates.set(TestingRunIssues.TICKET_SOURCE, GlobalEnums.TicketSource.JIRA.name()),
                                Updates.set(TestingRunIssues.TICKET_PROJECT_KEY, projId),
                                Updates.set(TestingRunIssues.TICKET_ID, issueKey),
                                Updates.set(TestingRunIssues.TICKET_LAST_UPDATED_AT, Context.now())
                            ),
                            new UpdateOptions().upsert(false)
                    );
                }

                for (int i = 0; i < issues.size(); i++) {
                    BasicDBObject issue = issues.get(i);
                    TestingRunResult testingRunResult = testingRunResultList.get(i);
                    String issueKey = issue.getString("key");
                    TestResult testResult = getTestResultFromTestingRunResult(testingRunResult);

                    setIssueId(issueKey);
                    if(testResult != null) {
                        setOrigReq(testResult.getOriginalMessage());
                        setTestReq(testResult.getMessage());
                    } else {
                        loggerMaker.errorAndAddToDb("TestResult obj not found.", LoggerMaker.LogDb.DASHBOARD);
                    }
                    String status = attachFileToIssue();
                    if (status.equals(ERROR.toUpperCase())) {
                        return ERROR.toUpperCase();
                    }
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error processing Jira bulk issue response " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
                return Action.ERROR.toUpperCase();
            }

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error making Jira bulk create request: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            return Action.ERROR.toUpperCase();
        }

        return Action.SUCCESS.toUpperCase();
    }

    private BasicDBObject jiraTicketPayloadCreator(JiraMetaData jiraMetaData) {
        BasicDBObject fields = new BasicDBObject();
        String endpoint = jiraMetaData.getEndPointStr().replace("Endpoint - ", "");
        String truncatedEndpoint = endpoint;
        if(endpoint.length() > 30) {
            truncatedEndpoint = endpoint.substring(0, 15) + "..." + endpoint.substring(endpoint.length() - 15);
        }

        String endpointMethod = jiraMetaData.getTestingIssueId().getApiInfoKey().getMethod().name();

        // issue title
        fields.put("summary", "Akto Report - " + jiraMetaData.getIssueTitle() + " (" + endpointMethod + " - " + truncatedEndpoint + ")");

        // Issue type (TASK)
        BasicDBObject issueTypeObj = new BasicDBObject();
        issueTypeObj.put("id", this.issueType);
        fields.put("issuetype", issueTypeObj);
        fields.put("labels", new String[] {JobConstants.TICKET_LABEL_AKTO_SYNC});


        // Project ID
        BasicDBObject project = new BasicDBObject();
        project.put("key", this.projId);
        fields.put("project", project);

        // Issue description
        BasicDBObject description = new BasicDBObject();
        description.put("type", "doc");
        description.put("version", 1);
        BasicDBList contentList = new BasicDBList();
        contentList.add(buildContentDetails(jiraMetaData.getHostStr(), null));
        contentList.add(buildContentDetails(jiraMetaData.getEndPointStr(), null));
        contentList.add(buildContentDetails("Issue link - Akto dashboard", jiraMetaData.getIssueUrl()));
        contentList.add(buildContentDetails(jiraMetaData.getIssueDescription(), null));
        description.put("content", contentList);

        fields.put("description", description);

        return fields;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getProjId() {
        return projId;
    }

    public void setProjId(String projId) {
        this.projId = projId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public String getIssueType() {
        return issueType;
    }

    public void setIssueType(String issueType) {
        this.issueType = issueType;
    }

    public JiraIntegration getJiraIntegration() {
        return jiraIntegration;
    }

    public void setJiraIntegration(JiraIntegration jiraIntegration) {
        this.jiraIntegration = jiraIntegration;
    }

    public String getOrigReq() {
        return origReq;
    }

    public void setOrigReq(String origReq) {
        this.origReq = origReq;
    }

    public String getTestReq() {
        return testReq;
    }

    public void setTestReq(String testReq) {
        this.testReq = testReq;
    }

    public String getIssueId() {
        return issueId;
    }

    public void setIssueId(String issueId) {
        this.issueId = issueId;
    }

    public Map<String, List<BasicDBObject>> getProjectAndIssueMap() {
        return projectAndIssueMap;
    }

    public void setProjectAndIssueMap(Map<String, List<BasicDBObject>> projectAndIssueMap) {
        this.projectAndIssueMap = projectAndIssueMap;
    }

    public JiraMetaData getJiraMetaData() {
        return jiraMetaData;
    }

    public void setJiraMetaData(JiraMetaData jiraMetaData) {
        this.jiraMetaData = jiraMetaData;
    }

    public String getJiraTicketKey() {
        return jiraTicketKey;
    }

    public void setJiraTicketKey(String jiraTicketKey) {
        this.jiraTicketKey = jiraTicketKey;
    }

    public void setIssuesIds(List<TestingIssuesId> issuesIds) {
        this.issuesIds = issuesIds;
    }

    public void setAktoDashboardHost(String aktoDashboardHost) {
        this.aktoDashboardHost = aktoDashboardHost;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Map<String, ProjectMapping> getProjectMappings() {
        return projectMappings;
    }

    public void setProjectMappings(Map<String, ProjectMapping> projectMappings) {
        this.projectMappings = projectMappings;
    }

    @Override
    public void setServletRequest(HttpServletRequest request) {
        this.dashboardUrl = com.akto.utils.Utils.createDashboardUrlFromRequest(request);
    }

    private void addAktoHostUrl() {
        if (DashboardMode.isOnPremDeployment()) {

            AktoHostUrlConfig existingConfig = (AktoHostUrlConfig) ConfigsDao.instance.findOne(
                Filters.eq(Constants.ID, ConfigType.AKTO_DASHBOARD_HOST_URL.name()));

            int now = Context.now();

            if (existingConfig == null || (now - existingConfig.getLastSyncedAt()) > 3600) {
                AktoHostUrlConfig config = new AktoHostUrlConfig();
                config.setHostUrl(this.dashboardUrl);
                config.setLastSyncedAt(now);
                ConfigsDao.instance.updateOne(Filters.eq(Constants.ID, ConfigType.AKTO_DASHBOARD_HOST_URL.name()),
                    new Document("$set", config)
                );
            }
        }
    }

    private void syncBidirectionalSettings(JiraIntegration jiraIntegration) {
        try {

            Map<String, ProjectMapping> projectMappings = jiraIntegration.getProjectMappings();

            if (projectMappings == null || projectMappings.isEmpty()) {
                loggerMaker.info("No project mappings found in Jira integration");
                return;
            }

            int syncedCount = 0;

            for (Map.Entry<String, ProjectMapping> entry : projectMappings.entrySet()) {
                String projectKey = entry.getKey();
                ProjectMapping projectMapping = entry.getValue();

                if (projectMapping == null) {
                    continue;
                }

                BiDirectionalSyncSettings biDirectionalSettings = projectMapping.getBiDirectionalSyncSettings();

                // Check if entry already exists
                BidirectionalSyncSettings existingSettings = BidirectionalSyncSettingsDao.instance.findOne(
                    Filters.and(
                        Filters.eq(BidirectionalSyncSettings.SOURCE, TicketSource.JIRA.name()),
                        Filters.eq(BidirectionalSyncSettings.PROJECT_KEY, projectKey)
                    )
                );

                boolean isEnabled = biDirectionalSettings != null && biDirectionalSettings.isEnabled();

                if (isEnabled) {
                    if (existingSettings != null) {
                        // Update existing entry
                        // Check if we need to restart the job (if previously inactive)
                        ObjectId jobId = existingSettings.getJobId();
                        if (!existingSettings.isActive()) {
                            Job job = JobScheduler.restartJob(jobId);
                            if (job == null) {
                                loggerMaker.error("Error while restarting recurring job for project key: {}", projectKey);
                                continue;
                            }
                            loggerMaker.info("Restarted recurring job for project key: {}", projectKey);
                            jobId = job.getId();
                        }
                        BidirectionalSyncSettingsDao.instance.updateOne(
                            Filters.eq(BidirectionalSyncSettings.ID, existingSettings.getId()),
                            Updates.combine(
                                Updates.set(BidirectionalSyncSettings.ACTIVE, true),
                                Updates.set(BidirectionalSyncSettings.LAST_SYNCED_AT, Context.now()),
                                Updates.set(BidirectionalSyncSettings.JOB_ID, jobId)
                            )
                        );
                    } else {
                        Job job = createRecurringJob(projectKey);

                        if (job == null) {
                            loggerMaker.error("Error while creating recurring job for project key: {}", projectKey);
                            continue;
                        }

                        loggerMaker.info("Created recurring job for project key: {}", projectKey);

                        BidirectionalSyncSettings bidirectionalSyncSettings = new BidirectionalSyncSettings();
                        bidirectionalSyncSettings.setSource(TicketSource.JIRA);
                        bidirectionalSyncSettings.setProjectKey(projectKey);
                        bidirectionalSyncSettings.setActive(true);
                        bidirectionalSyncSettings.setLastSyncedAt(Context.now());
                        bidirectionalSyncSettings.setJobId(job.getId());
                        BidirectionalSyncSettingsDao.instance.insertOne(bidirectionalSyncSettings);
                    }
                    syncedCount++;
                } else if (existingSettings != null) {
                    // Update existing entry to set active=false
                    BidirectionalSyncSettingsDao.instance.updateOne(
                        Filters.eq(BidirectionalSyncSettings.ID, existingSettings.getId()),
                        Updates.combine(
                            Updates.set(BidirectionalSyncSettings.ACTIVE, false),
                            Updates.set(BidirectionalSyncSettings.LAST_SYNCED_AT, Context.now())
                        )
                    );

                    // Cancel the recurring job if it exists
                    ObjectId jobId = existingSettings.getJobId();
                    if (jobId != null) {
                        try {
                            JobScheduler.deleteJob(jobId);
                            loggerMaker.info("Cancelled recurring job for project key: {}", projectKey);
                        } catch (Exception e) {
                            loggerMaker.error("Error cancelling recurring job for project key: {}", projectKey, e);
                        }
                    }

                    loggerMaker.info("Disabled bidirectional sync for project key: {}", projectKey);
                    syncedCount++;
                }
            }

            loggerMaker.info("Synced {} bidirectional settings", syncedCount);

        } catch (Exception e) {
            loggerMaker.error("Error syncing bidirectional settings: {}", e.getMessage(), e);
            addActionError("Error syncing bidirectional settings: " + e.getMessage());
        }
    }

    private Job createRecurringJob(String projectKey) {
        return JobScheduler.scheduleRecurringJob(
            Context.accountId.get(),
            new TicketSyncJobParams(
                TicketSource.JIRA.name(),
                projectKey,
                Context.now()
            ),
            JobExecutorType.DASHBOARD,
            TICKET_SYNC_JOB_RECURRING_INTERVAL_SECONDS
        );
    }
}
