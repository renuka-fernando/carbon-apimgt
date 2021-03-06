package org.wso2.carbon.apimgt.impl.dao;

import edu.emory.mathcs.backport.java.util.Collections;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.model.APIRevisionDeployment;
import org.wso2.carbon.apimgt.impl.dao.constants.SQLConstants;
import org.wso2.carbon.apimgt.impl.dto.APIRuntimeArtifactDto;
import org.wso2.carbon.apimgt.impl.utils.APIMgtDBUtil;
import org.wso2.carbon.apimgt.impl.utils.GatewayArtifactsMgtDBUtil;

import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class GatewayArtifactsMgtDAO {

    private static final Log log = LogFactory.getLog(GatewayArtifactsMgtDAO.class);
    private static GatewayArtifactsMgtDAO gatewayArtifactsMgtDAO = null;

    /**
     * Private constructor
     */
    private GatewayArtifactsMgtDAO() {

    }

    /**
     * Method to get the instance of the GatewayArtifactsMgtDAO.
     *
     * @return {@link GatewayArtifactsMgtDAO} instance
     */
    public static GatewayArtifactsMgtDAO getInstance() {

        if (gatewayArtifactsMgtDAO == null) {
            gatewayArtifactsMgtDAO = new GatewayArtifactsMgtDAO();
        }
        return gatewayArtifactsMgtDAO;
    }

    /**
     * Add details of the APIs published in the Gateway
     *
     * @param apiId        - UUID of the API
     * @param name         - Name of the API
     * @param version      - Version of the API
     * @param tenantDomain - Tenant domain of the API
     */
    private boolean addGatewayPublishedAPIDetails(Connection connection, String apiId, String name, String version,
                                                 String tenantDomain, String type)
            throws SQLException {

        boolean result = false;
        try (PreparedStatement preparedStatement = connection.prepareStatement(SQLConstants.CHECK_API_EXISTS)) {
            preparedStatement.setString(1, apiId);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return true;
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(SQLConstants.ADD_GW_PUBLISHED_API_DETAILS)) {
            statement.setString(1, apiId);
            statement.setString(2, name);
            statement.setString(3, version);
            statement.setString(4, tenantDomain);
            statement.setString(5, type);
            result = statement.executeUpdate() == 1;
            connection.commit();
        } catch (SQLException e) {
            APIMgtDBUtil.rollbackConnection(connection,
                    "Failed to rollback add API details for " + name, e);
        }
        return result;
    }

    private boolean addGatewayPublishedAPIArtifacts(Connection connection, String apiId, String revision,
                                                   InputStream inputStream)
            throws APIManagementException, SQLException {

        String dbQuery = SQLConstants.ADD_GW_API_ARTIFACT;

        boolean result = false;
        if (isAPIArtifactExists(connection, apiId, revision)) {
            updateGatewayPublishedAPIArtifacts(connection, apiId, revision, inputStream);
        } else {
            try (PreparedStatement statement = connection.prepareStatement(dbQuery)) {
                statement.setBinaryStream(1, inputStream);
                Timestamp timestamp = new Timestamp(System.currentTimeMillis());
                statement.setTimestamp(2, timestamp);
                statement.setString(3, apiId);
                statement.setString(4, revision);
                result = statement.executeUpdate() == 1;
                connection.commit();
            }
        }
        return result;
    }

    /**
     * Check whether the API artifact for given label exists in the db
     *
     * @param APIId - UUID of the API
     */
    private boolean isAPIArtifactExists(Connection connection, String APIId, String revisionId)
            throws SQLException {

        try (PreparedStatement statement = connection.prepareStatement(SQLConstants.CHECK_ARTIFACT_EXISTS)) {
            connection.setAutoCommit(false);
            connection.commit();
            statement.setString(1, APIId);
            statement.setString(2, revisionId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void handleException(String msg, Throwable t) throws APIManagementException {

        log.error(msg, t);
        throw new APIManagementException(msg, t);
    }

    /**
     * Retrieve the API ID of the API
     *
     * @param apiName      - Name of the API
     * @param version      - version of the API
     * @param tenantDomain - Tenant Domain of the API
     * @throws APIManagementException if an error occurs
     */
    public String getGatewayAPIId(String apiName, String version, String tenantDomain)
            throws APIManagementException {

        ResultSet rs = null;
        String apiID = null;
        try (Connection connection = GatewayArtifactsMgtDBUtil.getArtifactSynchronizerConnection();
             PreparedStatement statement = connection.prepareStatement(SQLConstants.GET_API_ID)) {
            connection.setAutoCommit(false);
            connection.commit();
            statement.setString(1, apiName);
            statement.setString(2, tenantDomain);
            statement.setString(3, version);
            rs = statement.executeQuery();
            while (rs.next()) {
                apiID = rs.getString("API_ID");
            }
        } catch (SQLException e) {
            handleException("Failed to get artifacts ", e);
        } finally {
            GatewayArtifactsMgtDBUtil.closeResultSet(rs);
        }
        return apiID;
    }

    /**
     * Retrieve the gateway labels which the API subscribed to
     *
     * @param apiID - API ID  of the API
     * @throws APIManagementException if an error occurs
     */
    public List<String> getGatewayAPILabels(String apiID)
            throws APIManagementException {

        ResultSet rs = null;
        List<String> labels = new ArrayList<>();
        try (Connection connection = GatewayArtifactsMgtDBUtil.getArtifactSynchronizerConnection();
             PreparedStatement statement = connection.prepareStatement(SQLConstants.GET_API_LABEL)) {
            connection.setAutoCommit(false);
            connection.commit();
            statement.setString(1, apiID);
            rs = statement.executeQuery();
            while (rs.next()) {
                labels.add(rs.getString("GATEWAY_LABEL"));
            }
        } catch (SQLException e) {
            handleException("Failed to get artifacts ", e);
        } finally {
            GatewayArtifactsMgtDBUtil.closeResultSet(rs);
        }
        return labels;
    }

    private void updateGatewayPublishedAPIArtifacts(Connection connection, String apiId, String revision,
                                                    InputStream fileInputStream)
            throws SQLException {

        String query = SQLConstants.UPDATE_API_ARTIFACT;
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setBinaryStream(1, fileInputStream);
            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
            statement.setTimestamp(2, timestamp);
            statement.setString(3, apiId);
            statement.setString(4, revision);
            statement.executeUpdate();
        }
    }

    public void addAndRemovePublishedGatewayLabels(String apiId, String revision, Set<String> gatewayLabelsToDeploy,
                                                   Set<APIRevisionDeployment> gatewayLabelsToRemove)
            throws APIManagementException {

        String deleteQuery = SQLConstants.DELETE_GW_PUBLISHED_LABELS_BY_API_ID_REVISION_ID_DEPLOYMENT;
        if (gatewayLabelsToDeploy.size() > 0 || gatewayLabelsToRemove.size() > 0) {
            try (Connection connection = GatewayArtifactsMgtDBUtil.getArtifactSynchronizerConnection()) {
                connection.setAutoCommit(false);
                try (PreparedStatement statement = connection.prepareStatement(deleteQuery)) {
                    for (APIRevisionDeployment apiRevisionDeployment : gatewayLabelsToRemove) {
                        statement.setString(1, apiId);
                        statement.setString(2, apiRevisionDeployment.getRevisionUUID());
                        statement.setString(3, apiRevisionDeployment.getDeployment());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                try {
                    if (gatewayLabelsToDeploy.size() > 0) {
                        addPublishedGatewayLabels(connection, apiId, revision, gatewayLabelsToDeploy);
                    }
                    connection.commit();
                } catch (SQLException e) {
                    connection.rollback();
                    throw new APIManagementException("Failed to attach labels", e);
                }
            } catch (SQLException e) {
                handleException("Failed to attach labels" + apiId, e);
            }
        }
    }

    private void addPublishedGatewayLabels(Connection connection, String apiUUID, String revisionId,
                                           Set<String> gateways)
            throws SQLException {

        String addQuery = SQLConstants.ADD_GW_PUBLISHED_LABELS;
        try (PreparedStatement preparedStatement = connection.prepareStatement(addQuery)) {
            for (String gatewayLabel : gateways) {
                preparedStatement.setString(1, apiUUID);
                preparedStatement.setString(2, revisionId);
                preparedStatement.setString(3, gatewayLabel);
                preparedStatement.addBatch();
            }
            preparedStatement.executeBatch();
        }
    }

    public void deleteGatewayArtifact(String apiId, String revision) throws APIManagementException {

        String deleteGWPublishedArtifacts = SQLConstants.DELETE_FROM_AM_GW_API_ARTIFACTS_WHERE_API_ID_AND_REVISION_ID;
        try (Connection connection = GatewayArtifactsMgtDBUtil.getArtifactSynchronizerConnection()) {
            connection.setAutoCommit(false);
            try {
                removePublishedGatewayLabels(connection, apiId, revision);
                try (PreparedStatement preparedStatement = connection.prepareStatement(deleteGWPublishedArtifacts)) {
                    preparedStatement.setString(1, apiId);
                    preparedStatement.setString(2, revision);
                    preparedStatement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                handleException("Failed to delete Gateway Artifact" + apiId, e);
            }
        } catch (SQLException | APIManagementException e) {
            handleException("Failed to delete Gateway Artifact" + apiId, e);
        }
    }

    public void deleteGatewayArtifacts(String apiId) throws APIManagementException {

        String deleteGWArtifact = SQLConstants.DELETE_GW_PUBLISHED_API_DETAILS;
        try (Connection connection = GatewayArtifactsMgtDBUtil.getArtifactSynchronizerConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement preparedStatement = connection.prepareStatement(deleteGWArtifact)) {
                    preparedStatement.setString(1, apiId);
                    preparedStatement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                handleException("Failed to delete Gateway Artifact" + apiId, e);
            }
        } catch (SQLException | APIManagementException e) {
            handleException("Failed to delete Gateway Artifact" + apiId, e);
        }
    }

    public List<APIRuntimeArtifactDto> retrieveGatewayArtifactsByAPIIDAndLabel(String apiId, String[] labels,
                                                                               String tenantDomain)
            throws APIManagementException {

        String query = SQLConstants.RETRIEVE_ARTIFACTS_BY_APIID_AND_LABEL;
        query = query.replaceAll("_GATEWAY_LABELS_", String.join(",",Collections.nCopies(labels.length, "?")));
        List<APIRuntimeArtifactDto> apiRuntimeArtifactDtoList = new ArrayList<>();
        try (Connection connection = GatewayArtifactsMgtDBUtil.getArtifactSynchronizerConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, apiId);
            int index = 2;
            for (String label : labels) {
                preparedStatement.setString(index, label);
                index++;
            }
            preparedStatement.setString(index, tenantDomain);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    APIRuntimeArtifactDto apiRuntimeArtifactDto = new APIRuntimeArtifactDto();
                    apiRuntimeArtifactDto.setTenantDomain(resultSet.getString("TENANT_DOMAIN"));
                    apiRuntimeArtifactDto.setApiId(apiId);
                    apiRuntimeArtifactDto.setLabel(resultSet.getString("LABEL"));
                    apiRuntimeArtifactDto.setName(resultSet.getString("API_NAME"));
                    apiRuntimeArtifactDto.setVersion(resultSet.getString("API_VERSION"));
                    apiRuntimeArtifactDto.setProvider(resultSet.getString("API_PROVIDER"));
                    apiRuntimeArtifactDto.setRevision(resultSet.getString("REVISION_ID"));
                    apiRuntimeArtifactDto.setType(resultSet.getString("API_TYPE"));
                    InputStream artifact = resultSet.getBinaryStream("ARTIFACT");
                    if (artifact != null) {
                        byte[] artifactByte = APIMgtDBUtil.getBytesFromInputStream(artifact);
                        try (InputStream newArtifact = new ByteArrayInputStream(artifactByte)) {
                            apiRuntimeArtifactDto.setArtifact(newArtifact);
                        } catch (IOException e) {
                            handleException("Error occurred retrieving input stream from byte array.", e);
                        }
                    }
                    apiRuntimeArtifactDto.setFile(true);
                    apiRuntimeArtifactDtoList.add(apiRuntimeArtifactDto);
                }
            }
        } catch (SQLException e) {
            handleException("Failed to retrieve Gateway Artifact for Api : " + apiId + " and labels: " + StringUtils.join(",", labels), e);
        }

        return apiRuntimeArtifactDtoList;
    }

    public List<APIRuntimeArtifactDto> retrieveGatewayArtifactsByLabel(String[] labels, String tenantDomain)
            throws APIManagementException {

        String query = SQLConstants.RETRIEVE_ARTIFACTS_BY_LABEL;
        query = query.replaceAll("_GATEWAY_LABELS_", String.join(",",Collections.nCopies(labels.length, "?")));
        List<APIRuntimeArtifactDto> apiRuntimeArtifactDtoList = new ArrayList<>();
        try (Connection connection = GatewayArtifactsMgtDBUtil.getArtifactSynchronizerConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            int index = 1;
            for (String label : labels) {
                preparedStatement.setString(index, label);
                index++;
            }
            preparedStatement.setString(index, tenantDomain);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    APIRuntimeArtifactDto apiRuntimeArtifactDto = new APIRuntimeArtifactDto();
                    apiRuntimeArtifactDto.setTenantDomain(resultSet.getString("TENANT_DOMAIN"));
                    apiRuntimeArtifactDto.setApiId(resultSet.getString("API_ID"));
                    apiRuntimeArtifactDto.setLabel(resultSet.getString("LABEL"));
                    apiRuntimeArtifactDto.setName(resultSet.getString("API_NAME"));
                    apiRuntimeArtifactDto.setVersion(resultSet.getString("API_VERSION"));
                    apiRuntimeArtifactDto.setProvider(resultSet.getString("API_PROVIDER"));
                    apiRuntimeArtifactDto.setRevision(resultSet.getString("REVISION_ID"));
                    apiRuntimeArtifactDto.setType(resultSet.getString("API_TYPE"));
                    InputStream artifact = resultSet.getBinaryStream("ARTIFACT");
                    if (artifact != null) {
                        byte[] artifactByte = APIMgtDBUtil.getBytesFromInputStream(artifact);
                        try (InputStream newArtifact = new ByteArrayInputStream(artifactByte)) {
                            apiRuntimeArtifactDto.setArtifact(newArtifact);
                        } catch (IOException e) {
                            handleException("Error occurred retrieving input stream from byte array.", e);
                        }
                    }
                    apiRuntimeArtifactDto.setFile(true);
                    apiRuntimeArtifactDtoList.add(apiRuntimeArtifactDto);
                }
            }
        } catch (SQLException e) {
            handleException("Failed to retrieve Gateway Artifact for labels : " + StringUtils.join(",", labels), e);
        }

        return apiRuntimeArtifactDtoList;
    }

    public List<APIRuntimeArtifactDto> retrieveGatewayArtifacts(String tenantDomain)
            throws APIManagementException {

        String query = SQLConstants.RETRIEVE_ARTIFACTS;
        List<APIRuntimeArtifactDto> apiRuntimeArtifactDtoList = new ArrayList<>();
        try (Connection connection = GatewayArtifactsMgtDBUtil.getArtifactSynchronizerConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, tenantDomain);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    APIRuntimeArtifactDto apiRuntimeArtifactDto = new APIRuntimeArtifactDto();
                    apiRuntimeArtifactDto.setTenantDomain(resultSet.getString("TENANT_DOMAIN"));
                    apiRuntimeArtifactDto.setApiId(resultSet.getString("API_ID"));
                    apiRuntimeArtifactDto.setLabel(resultSet.getString("LABEL"));
                    apiRuntimeArtifactDto.setName(resultSet.getString("API_NAME"));
                    apiRuntimeArtifactDto.setVersion(resultSet.getString("API_VERSION"));
                    apiRuntimeArtifactDto.setProvider(resultSet.getString("API_PROVIDER"));
                    apiRuntimeArtifactDto.setRevision(resultSet.getString("REVISION_ID"));
                    apiRuntimeArtifactDto.setType(resultSet.getString("API_TYPE"));
                    InputStream artifact = resultSet.getBinaryStream("ARTIFACT");
                    if (artifact != null) {
                        byte[] artifactByte = APIMgtDBUtil.getBytesFromInputStream(artifact);
                        try (InputStream newArtifact = new ByteArrayInputStream(artifactByte)) {
                            apiRuntimeArtifactDto.setArtifact(newArtifact);
                        } catch (IOException e) {
                            handleException("Error occurred retrieving input stream from byte array.", e);
                        }
                    }
                    apiRuntimeArtifactDto.setFile(true);
                    apiRuntimeArtifactDtoList.add(apiRuntimeArtifactDto);
                }
            }
        } catch (SQLException e) {
            handleException("Failed to retrieve Gateway Artifact", e);
        }

        return apiRuntimeArtifactDtoList;
    }

    public void removePublishedGatewayLabels(String apiId, String apiRevisionId) throws APIManagementException {

        try (Connection connection = GatewayArtifactsMgtDBUtil.getArtifactSynchronizerConnection()) {
            try {
                connection.setAutoCommit(false);
                removePublishedGatewayLabels(connection, apiId, apiRevisionId);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            handleException("Failed to delete Gateway Artifact", e);
        }
    }

    private void removePublishedGatewayLabels(Connection connection, String apiId, String revision)
            throws SQLException {

        try (PreparedStatement preparedStatement = connection
                .prepareStatement(SQLConstants.DELETE_GW_PUBLISHED_LABELS)) {
            preparedStatement.setString(1, apiId);
            preparedStatement.setString(2, revision);
            preparedStatement.executeUpdate();
        }
    }

    public void addAndRemovePublishedGatewayLabels(String apiId, String revision, Set<String> gateways)
            throws APIManagementException {

        try (Connection connection = GatewayArtifactsMgtDBUtil.getArtifactSynchronizerConnection()) {
            try {
                connection.setAutoCommit(false);
                removePublishedGatewayLabels(connection, apiId, revision);
                addPublishedGatewayLabels(connection, apiId, revision, gateways);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            handleException("Failed to delete and add  Gateway environments ", e);
        }
    }


    public void addGatewayAPIArtifactAndMetaData(String apiUUID, String apiName, String version, String revisionUUID,
                                                 String tenantDomain, String apiType, File artifact)
            throws APIManagementException {
        try (Connection connection = GatewayArtifactsMgtDBUtil.getArtifactSynchronizerConnection()) {
            try {
                connection.setAutoCommit(false);
                addGatewayPublishedAPIDetails(connection, apiUUID, apiName, version, tenantDomain, apiType);
                try (FileInputStream fileInputStream = new FileInputStream(artifact)) {
                    addGatewayPublishedAPIArtifacts(connection, apiUUID, revisionUUID, fileInputStream);
                }
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException | IOException e) {
            handleException("Failed to Add Artifact to Database", e);
        }
    }
}