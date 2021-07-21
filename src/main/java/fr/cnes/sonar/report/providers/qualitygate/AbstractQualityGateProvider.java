/*
 * This file is part of cnesreport.
 *
 * cnesreport is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * cnesreport is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with cnesreport.  If not, see <http://www.gnu.org/licenses/>.
 */

package fr.cnes.sonar.report.providers.qualitygate;

import fr.cnes.sonar.report.exceptions.BadSonarQubeRequestException;
import fr.cnes.sonar.report.exceptions.SonarQubeException;
import fr.cnes.sonar.report.exceptions.UnknownQualityGateException;
import fr.cnes.sonar.report.model.QualityGate;
import fr.cnes.sonar.report.providers.AbstractDataProvider;
import fr.cnes.sonar.report.utils.UrlEncoder;

import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.navigation.ComponentRequest;
import org.sonarqube.ws.client.qualitygates.ListRequest;
import org.sonarqube.ws.client.qualitygates.ProjectStatusRequest;
import org.sonarqube.ws.client.qualitygates.ShowRequest;
import org.sonarqube.ws.Measures.ComponentWsResponse;
import org.sonarqube.ws.Qualitygates.ListWsResponse;
import org.sonarqube.ws.Qualitygates.ProjectStatusResponse;
import org.sonarqube.ws.Qualitygates.ShowWsResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.apache.commons.math3.util.Precision;

/**
 * Contains common code for quality gate providers
 */
public abstract class AbstractQualityGateProvider extends AbstractDataProvider {

    /**
     *  Name of the request for getting quality gates' details
     */
    private static final String GET_QUALITY_GATES_DETAILS_REQUEST =
            "GET_QUALITY_GATES_DETAILS_REQUEST";
    /**
     *  Name of the request for getting quality gates
     */
    private static final String GET_QUALITY_GATES_REQUEST = "GET_QUALITY_GATES_REQUEST";
    /**
     * Name of the request for getting the quality gate status of a project
     */
    private static final String GET_QUALITY_GATE_STATUS_REQUEST = 
            "GET_QUALITY_GATE_STATUS_REQUEST";
    /**
     * Name of the request for getting a specific metric
     */
    private static final String GET_METRIC_REQUEST = "GET_METRIC_REQUEST";
    /**
     * Field to search in json to get the boolean saying if a profile is the default one
     */
    private static final String DEFAULT = "default";
    /**
     * Field to search in json to get quality gates
     */
    private static final String QUALITYGATES = "qualitygates";
    /** 
     * Field to find in json to get quality gate
     */
    private static final String QUALITY_GATE = "qualityGate";
    /**
     * Parameter "projectStatus" of the JSON response
     */
    private static final String PROJECT_STATUS = "projectStatus";
    /**
     * Parameter "conditions" of the JSON response
     */
    private static final String CONDITIONS = "conditions";
    /**
     * Parameter "status" of the JSON response
     */
    private static final String STATUS = "status";
    /**
     * Parameter "metricKey" of the JSON response
     */
    private static final String METRIC_KEY = "metricKey";
    /**
     * Parameter "metrics" of the JSON response
     */
    private static final String METRICS = "metrics";
    /**
     * Parameter "name" of the JSON response
     */
    private static final String NAME = "name";
    /**
     * Value of the parameter "status" of the JSON response
     */
    private static final String ERROR = "ERROR";
    /**
     * Parameter "actualValue" of the JSON response
     */
    private static final String ACTUAL_VALUE = "actualValue";
    /**
     * Parameter "errorThreshold" of the JSON response
     */
    private static final String ERROR_THRESHOLD = "errorThreshold";
    /**
     * Parameter "comparator" of the JSON response
     */
    private static final String COMPARATOR = "comparator";
    /**
     * Parameter "type" of the JSON response
     */
    private static final String TYPE = "type";
    /**
     * Value of the parameter "type" of the JSON response
     */
    private static final String RATING = "RATING";
    /**
     * Value of the parameter "type" of the JSON response
     */
    private static final String WORK_DUR = "WORK_DUR";
    /**
     * Value of the parameter "type" of the JSON response
     */
    private static final String PERCENT = "PERCENT";
    /**
     * Value of the parameter "type" of the JSON response
     */
    private static final String MILLISEC = "MILLISEC";

    /**
     * Complete constructor.
     * @param pServer SonarQube server.
     * @param pToken String representing the user token.
     * @param pProject The id of the project to report.
     * @param pBranch The branch of the project to report.
     */
    protected AbstractQualityGateProvider(final String pServer, final String pToken, final String pProject,
            final String pBranch) {
        super(pServer, pToken, pProject, pBranch);
    }

    /**
     * Complete constructor.
     * @param wsClient The web client.
     * @param project The id of the project to report.
     * @param branch The branch of the project to report.
     */
    protected AbstractQualityGateProvider(final WsClient wsClient, final String project, final String branch) {
        super(wsClient, project, branch);
    }

    /**
     * Generic getter for all the quality gates.
     * @param isCalledInStandalone True if the method is called in standalone mode.
     * @return Array containing all the issues.
     * @throws BadSonarQubeRequestException A request is not recognized by the server.
     * @throws SonarQubeException When SonarQube server is not callable.
     */
    protected List<QualityGate> getQualityGatesAbstract(final boolean isCalledInStandalone) throws BadSonarQubeRequestException, SonarQubeException {
        // result list
        final List<QualityGate> res = new ArrayList<>();

        String request;
        JsonObject jo;
        if (isCalledInStandalone) {
            // Get all quality gates
            request = String.format(getRequest(GET_QUALITY_GATES_REQUEST), getServer());
            // perform the request to the server
            jo = request(request);
        } else {
            // Get all quality gates
            final ListRequest listRequest = new ListRequest();
            // perform the request to the server
            final ListWsResponse listWsResponse = getWsClient().qualitygates().list(listRequest);
            // transform response to JsonObject
            jo = responseToJsonObject(listWsResponse);
        }

        // Get quality gates criteria
        final String defaultQG = (getGson().fromJson(jo.get(DEFAULT), String.class));
        final QualityGate[] tmp = (getGson().fromJson(jo.get(QUALITYGATES), QualityGate[].class));
        // for each quality gate
        for (QualityGate i : tmp) {
            if (isCalledInStandalone) {
                // request the criteria
                request = String.format(getRequest(GET_QUALITY_GATES_DETAILS_REQUEST),
                        getServer(), UrlEncoder.urlEncodeString(i.getName()));
                // perform previous request
                jo = request(request);
            } else {
                // request the criteria
                final ShowRequest showRequest = new ShowRequest().setName(i.getName());
                // perform previous request
                final ShowWsResponse showWsResponse = getWsClient().qualitygates().show(showRequest);
                // transform response to JsonObject
                jo = responseToJsonObject(showWsResponse);
            }

            // put it in configuration field
            i.setConf(jo.toString());
            
            // check if it is the default quality gate
            i.setDefault(i.getId().equals(defaultQG));

            // add the quality gate to the result list
            res.add(i);
        }

        return res;
    }

    /**
     * Generic getter for the quality gate corresponding to the project.
     * @param isCalledInStandalone True if the method is called in standalone mode.
     * @return The Quality Gate.
     * @throws UnknownQualityGateException When there is an error on a quality gate.
     * @throws BadSonarQubeRequestException When the request is incorrect.
     * @throws SonarQubeException When SonarQube server is not callable.
     */
    protected QualityGate getProjectQualityGateAbstract(final boolean isCalledInStandalone)
            throws UnknownQualityGateException, BadSonarQubeRequestException, SonarQubeException {
        // Final quality gate result.
        QualityGate res = null;

        // get all the quality gates
        final List<QualityGate> qualityGates = getQualityGatesAbstract(isCalledInStandalone);

        JsonObject jsonObject;
        if (isCalledInStandalone) {
            // get the project
            String request = String.format(getRequest(GET_PROJECT_REQUEST),
                    getServer(), getProjectKey(), getBranch());

            // perform previous request
            jsonObject = request(request);
        } else {
            // get the project
            final ComponentRequest componentRequest = new ComponentRequest()
                                                            .setComponent(getProjectKey())
                                                            .setBranch(getBranch());
            // perform previous request
            final String componentResponse = getWsClient().navigation().component(componentRequest);
            // transform response to JsonObject
            jsonObject = getGson().fromJson(componentResponse, JsonObject.class);
        }

        // search for the good quality gate
        final Iterator<QualityGate> iterator = qualityGates.iterator();
        
        QualityGate tmp;
        boolean find = false;
        final String key = jsonObject.getAsJsonObject(QUALITY_GATE).get(KEY).getAsString();

        while (iterator.hasNext() && !find) {
            tmp = iterator.next();
            if (tmp.getId().equals(key)) {
                res = tmp;
                find = true;
            }
        }

        // check if it was found
        if (!find) {
            throw new UnknownQualityGateException(key);
        }       

        return res;
    }

    /**
     * Generic getter for the quality gate status of a project
     * @param isCalledInStandalone True if the method is called in standalone mode.
     * @return Map containing each condition of the quality gate and its status
     * @throws BadSonarQubeRequestException when the server does not understand the request
     * @throws SonarQubeException When SonarQube server is not callable.
     */
    protected Map<String, String> getQualityGateStatusAbstract(final boolean isCalledInStandalone) throws BadSonarQubeRequestException, SonarQubeException {
        JsonObject projectStatusResult;
        // request to get the quality gate status
        if (isCalledInStandalone) {
            projectStatusResult = request(String.format(getRequest(GET_QUALITY_GATE_STATUS_REQUEST),
                    getServer(), getBranch(), getProjectKey()));
        } else {
            final ProjectStatusRequest projectStatusRequest = new ProjectStatusRequest()
                                                                    .setBranch(getBranch())
                                                                    .setProjectKey(getProjectKey());
            final ProjectStatusResponse projectStatusResponse = getWsClient().qualitygates().projectStatus(projectStatusRequest);
            projectStatusResult = responseToJsonObject(projectStatusResponse);
        }
        // map containing the result
        Map<String, String> res = new LinkedHashMap<>();
        // retrieve the content of the object
        JsonObject projectStatusObject = projectStatusResult.get(PROJECT_STATUS).getAsJsonObject();
        // retrieve the array of conditions
        JsonArray conditions = projectStatusObject.get(CONDITIONS).getAsJsonArray();
        // add a couple metric name / status to the map for each condition
        for (JsonElement condition : conditions) {
            JsonObject conditionObject = condition.getAsJsonObject();
            String status = conditionObject.get(STATUS).getAsString();
            String metricKey = conditionObject.get(METRIC_KEY).getAsString();
            JsonObject metricResult;
            if (isCalledInStandalone) {
                metricResult = request(String.format(getRequest(GET_METRIC_REQUEST),
                    getServer(), getBranch(), getProjectKey(), metricKey));
            } else {
                final List<String> additionalFields = new ArrayList<>(Arrays.asList(METRICS));
                final List<String> metricKeys = new ArrayList<>(Arrays.asList(metricKey));
                final org.sonarqube.ws.client.measures.ComponentRequest componentRequest =
                    new org.sonarqube.ws.client.measures.ComponentRequest()
                                                            .setAdditionalFields(additionalFields)
                                                            .setBranch(getBranch())
                                                            .setComponent(getProjectKey())
                                                            .setMetricKeys(metricKeys);
                final ComponentWsResponse componentWsResponse = getWsClient().measures().component(componentRequest);
                metricResult = responseToJsonObject(componentWsResponse);
            }
            String name = metricResult.get(METRICS).getAsJsonArray().get(0).getAsJsonObject().get(NAME).getAsString();
            // add the detailed explanation on why the condition failed if it's the case
            if (status.equals(ERROR)) {
                String actualValue = conditionObject.get(ACTUAL_VALUE).getAsString();
                String errorThreshold = conditionObject.get(ERROR_THRESHOLD).getAsString();
                String comparator = conditionObject.get(COMPARATOR).getAsString();
                String type = metricResult.get(METRICS).getAsJsonArray().get(0).getAsJsonObject().get(TYPE).getAsString();
                status = status.concat(getErrorExplanation(actualValue, errorThreshold, comparator, type));
            }
            res.put(name, status);
        }
        return res;
    }

    /**
     * Construct the sentence explaining why a condition failed
     * @param actualValue the actual value in the JSON response
     * @param errorThreshold the error threshold in the JSON response
     * @param comparator the comparator in the JSON response
     * @param type the type in the JSON response
     * @return a String containing the error explanation
     */
    protected String getErrorExplanation(String actualValue, String errorThreshold, String comparator, String type) {
        // format of the string to add in the status
        String format = " (%s is %s than %s)";
        
        String actual;
        String compare;
        String threshold;

        // compute the values of the strings to put in the explanation according to their types
        switch (type) {
            case RATING:
                actual = ratingToLetter(actualValue);
                compare = "worse";
                threshold = ratingToLetter(errorThreshold);
                break;
            case WORK_DUR:
                actual = workDurationToTime(actualValue);
                compare = comparatorToString(comparator);
                threshold = workDurationToTime(errorThreshold);
                break;
            case PERCENT:
                actual = String.valueOf(Precision.round(Double.valueOf(actualValue), 1)).concat("%");
                compare = comparatorToString(comparator);
                threshold = errorThreshold.concat("%");
                break;
            case MILLISEC:
                actual = actualValue.concat("ms");
                compare = comparatorToString(comparator);
                threshold = errorThreshold.concat("ms");
                break;
            default:
                actual = actualValue;
                compare = comparatorToString(comparator);
                threshold = errorThreshold;
                break;
        }
        
        return String.format(format, actual, compare, threshold);
    }

    /**
     * Convert a rating number to a String
     * @param rating the rating number
     * @return the letter corresponding to the rating number
     */
    private String ratingToLetter(String rating) {
        String res;
        switch (rating) {
            case "1":
                res = "A";
                break;
            case "2":
                res = "B";
                break;
            case "3":
                res = "C";
                break;
            case "4":
                res = "D";
                break;
            case "5":
                res = "E";
                break;
            default:
                res = rating;
                break;
        }
        return res;
    }

    /**
     * Convert a work duration number to days/hours/minutes format
     * @param workDuration the work duration number
     * @return the time corresponding to the work duration number
     */
    private String workDurationToTime(String workDuration) {
        String format = "%sd %sh %smin";
        int workDurationInt = Integer.parseInt(workDuration);
        return String.format(format, workDurationInt/8/60, workDurationInt/60%8, workDurationInt%60);
    }

    /**
     * Convert a comparator in a JSON response to an understandable string
     * @param comparator the comparator in the JSON response
     * @return the understandable string corresponding to the comparator
     */
    private String comparatorToString(String comparator) {
        String res;
        if (comparator.equals("GT")) {
            res = "greater";
        } else {
            res = "less";
        }
        return res;
    }
}