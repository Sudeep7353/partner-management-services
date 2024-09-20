package io.mosip.pms.partner.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.kernel.core.authmanager.authadapter.model.AuthUserDetails;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.pms.common.entity.*;
import io.mosip.pms.common.repository.*;
import io.mosip.pms.common.response.dto.ResponseWrapperV2;
import io.mosip.pms.common.util.PMSLogger;
import io.mosip.pms.device.authdevice.repository.DeviceDetailRepository;
import io.mosip.pms.device.authdevice.repository.FTPChipDetailRepository;
import io.mosip.pms.device.authdevice.repository.SecureBiometricInterfaceRepository;
import io.mosip.pms.device.authdevice.service.impl.FTPChipDetailServiceImpl;
import io.mosip.pms.partner.constant.ErrorCode;
import io.mosip.pms.partner.dto.*;
import io.mosip.pms.partner.exception.PartnerServiceException;
import io.mosip.pms.partner.request.dto.PartnerCertDownloadRequestDto;
import io.mosip.pms.partner.response.dto.PartnerCertDownloadResponeDto;
import io.mosip.pms.partner.service.MultiPartnerService;
import io.mosip.pms.partner.util.PartnerHelper;
import io.mosip.pms.partner.util.PartnerUtil;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.security.cert.X509Certificate;
import java.util.*;

@Service
public class MultiPartnerServiceImpl implements MultiPartnerService {

    private static final Logger LOGGER = PMSLogger.getLogger(MultiPartnerServiceImpl.class);
    public static final String BLANK_STRING = "";
    public static final String DEVICE_PROVIDER = "Device_Provider";
    public static final String FTM_PROVIDER = "FTM_Provider";
    public static final String AUTH_PARTNER = "Auth_Partner";
    public static final String APPROVED = "approved";
    public static final String ACTIVE = "ACTIVE";
    public static final String INACTIVE = "INACTIVE";
    public static final String YES = "YES";
    public static final String PENDING_APPROVAL = "pending_approval";
    public static final String VERSION = "1.0";

    @Value("${mosip.pms.api.id.partner.certificates.get}")
    private String getPartnerCertificatesId;

    @Value("${mosip.pms.api.id.policy.requests.get}")
    private String getPolicyRequestsId;

    @Value("${mosip.pms.api.id.approved.partner.ids.with.policy.groups.get}")
    private String getApprovedPartnerIdsWithPolicyGroupsId;

    @Value("${mosip.pms.api.id.auth.partners.policies.get}")
    private String getAuthPartnersPoliciesId;

    @Value("${mosip.pms.api.id.api.keys.for.auth.partners.get}")
    private String getApiKeysForAuthPartnersId;

    @Autowired
    PartnerServiceRepository partnerRepository;

    @Autowired
    PolicyGroupRepository policyGroupRepository;

    @Autowired
    AuthPolicyRepository authPolicyRepository;

    @Autowired
    PartnerPolicyRepository partnerPolicyRepository;

    @Autowired
    PartnerHelper partnerHelper;

    @Override
    public ResponseWrapperV2<List<CertificateDto>> getPartnerCertificates() {
        ResponseWrapperV2<List<CertificateDto>> responseWrapper = new ResponseWrapperV2<>();
        try {
            String userId = getUserId();
            List<Partner> partnerList = partnerRepository.findByUserId(userId);
            if (!partnerList.isEmpty()) {
                List<CertificateDto> certificateDtoList = new ArrayList<>();
                for (Partner partner : partnerList) {
                    CertificateDto certificateDto = new CertificateDto();
                    try {
                        if (Objects.isNull(partner.getId()) || partner.getId().equals(BLANK_STRING)) {
                            LOGGER.info("Partner Id is null or empty for user id : " + userId);
                            throw new PartnerServiceException(ErrorCode.PARTNER_ID_NOT_EXISTS.getErrorCode(),
                                    ErrorCode.PARTNER_ID_NOT_EXISTS.getErrorMessage());
                        }
                        PartnerCertDownloadRequestDto requestDto = new PartnerCertDownloadRequestDto();
                        requestDto.setPartnerId(partner.getId());
                        PartnerCertDownloadResponeDto partnerCertDownloadResponeDto = partnerHelper.getCertificateFromKeyMgr(requestDto, "pmp.partner.certificaticate.get.rest.uri", PartnerCertDownloadResponeDto.class);
                        X509Certificate cert = PartnerUtil.decodeCertificateData(partnerCertDownloadResponeDto.getCertificateData());

                        certificateDto.setIsCertificateAvailable(true);
                        certificateDto.setCertificateIssuedTo(getCertificateName(cert.getSubjectDN().getName()));
                        certificateDto.setCertificateUploadDateTime(cert.getNotBefore());
                        certificateDto.setCertificateExpiryDateTime(cert.getNotAfter());
                        certificateDto.setPartnerId(partner.getId());
                        certificateDto.setPartnerType(partner.getPartnerTypeCode());
                    } catch (PartnerServiceException ex) {
                        LOGGER.info("Could not fetch partner certificate :" + ex.getMessage());
                        certificateDto.setIsCertificateAvailable(false);
                        certificateDto.setPartnerId(partner.getId());
                        certificateDto.setPartnerType(partner.getPartnerTypeCode());
                    }
                    certificateDtoList.add(certificateDto);
                }
                responseWrapper.setResponse(certificateDtoList);
            } else {
                LOGGER.info("sessionId", "idType", "id", "User id does not exists.");
                throw new PartnerServiceException(ErrorCode.USER_ID_NOT_EXISTS.getErrorCode(),
                        ErrorCode.USER_ID_NOT_EXISTS.getErrorMessage());
            }
        } catch (PartnerServiceException ex) {
            LOGGER.info("sessionId", "idType", "id", "In getPartnerCertificates method of MultiPartnerServiceImpl - " + ex.getMessage());
            responseWrapper.setErrors(PartnerUtil.setErrorResponse(ex.getErrorCode(), ex.getErrorText()));
        } catch (Exception ex) {
            LOGGER.debug("sessionId", "idType", "id", ex.getStackTrace());
            LOGGER.error("sessionId", "idType", "id",
                    "In getPartnerCertificates method of MultiPartnerServiceImpl - " + ex.getMessage());
            String errorCode = ErrorCode.PARTNER_CERTIFICATES_FETCH_ERROR.getErrorCode();
            String errorMessage = ErrorCode.PARTNER_CERTIFICATES_FETCH_ERROR.getErrorMessage();
            responseWrapper.setErrors(PartnerUtil.setErrorResponse(errorCode, errorMessage));
        }
        responseWrapper.setId(getPartnerCertificatesId);
        responseWrapper.setVersion(VERSION);
        return responseWrapper;
    }

    @Override
    @Transactional
    public ResponseWrapperV2<List<PolicyDto>> getPolicyRequests() {
        ResponseWrapperV2<List<PolicyDto>> responseWrapper = new ResponseWrapperV2<>();
        try {
            String userId = getUserId();
            List<Partner> partnerList = partnerRepository.findByUserId(userId);
            if (!partnerList.isEmpty()) {
                List<PolicyDto> policyDtoList = new ArrayList<>();
                for (Partner partner : partnerList) {
                    if (!skipDeviceOrFtmPartner(partner)) {
                        validatePartnerId(partner, userId);
                        validatePolicyGroupId(partner, userId);
                        PolicyGroup policyGroup = validatePolicyGroup(partner);
                        List<PartnerPolicyRequest> partnerPolicyRequestList = partner.getPartnerPolicyRequests();
                        if (!partnerPolicyRequestList.isEmpty()) {
                            for (PartnerPolicyRequest partnerPolicyRequest : partnerPolicyRequestList) {
                                AuthPolicy policyDetails = authPolicyRepository.findByPolicyGroupAndId(partner.getPolicyGroupId(), partnerPolicyRequest.getPolicyId());
                                if (Objects.nonNull(policyDetails)) {
                                    PolicyDto policyDto = new PolicyDto();
                                    policyDto.setPartnerId(partner.getId());
                                    policyDto.setPartnerType(partner.getPartnerTypeCode());

                                    policyDto.setPolicyGroupId(policyGroup.getId());
                                    policyDto.setPolicyGroupDescription(policyGroup.getDesc());
                                    policyDto.setPolicyGroupName(policyGroup.getName());

                                    policyDto.setPolicyId(policyDetails.getId());
                                    policyDto.setPolicyDescription(policyDetails.getDescr());
                                    policyDto.setPolicyName(policyDetails.getName());

                                    policyDto.setPartnerComments(partnerPolicyRequest.getRequestDetail());
                                    policyDto.setUpdatedDateTime(partnerPolicyRequest.getUpdDtimes());
                                    policyDto.setCreatedDateTime(partnerPolicyRequest.getCrDtimes());
                                    policyDto.setStatus(partnerPolicyRequest.getStatusCode());
                                    policyDtoList.add(policyDto);
                                } else {
                                    LOGGER.info("No matching policy not found for policy group ID :" + partner.getPolicyGroupId() + "and Policy ID :" + partnerPolicyRequest.getPolicyId());
                                    throw new PartnerServiceException(ErrorCode.MATCHING_POLICY_NOT_FOUND.getErrorCode(),
                                            ErrorCode.MATCHING_POLICY_NOT_FOUND.getErrorMessage());
                                }
                            }
                        }
                    }
                }
                responseWrapper.setResponse(policyDtoList);
            } else {
                LOGGER.info("sessionId", "idType", "id", "User id does not exists.");
                throw new PartnerServiceException(ErrorCode.USER_ID_NOT_EXISTS.getErrorCode(),
                        ErrorCode.USER_ID_NOT_EXISTS.getErrorMessage());
            }
        } catch (PartnerServiceException ex) {
            LOGGER.info("sessionId", "idType", "id", "In getPolicyRequests method of MultiPartnerServiceImpl - " + ex.getMessage());
            responseWrapper.setErrors(PartnerUtil.setErrorResponse(ex.getErrorCode(), ex.getErrorText()));
        } catch (Exception ex) {
            LOGGER.debug("sessionId", "idType", "id", ex.getStackTrace());
            LOGGER.error("sessionId", "idType", "id",
                    "In getPolicyRequests method of MultiPartnerServiceImpl - " + ex.getMessage());
            String errorCode = ErrorCode.PARTNER_POLICY_FETCH_ERROR.getErrorCode();
            String errorMessage = ErrorCode.PARTNER_POLICY_FETCH_ERROR.getErrorMessage();
            responseWrapper.setErrors(PartnerUtil.setErrorResponse(errorCode, errorMessage));
        }
        responseWrapper.setId(getPolicyRequestsId);
        responseWrapper.setVersion(VERSION);
        return responseWrapper;
    }

    @Override
    public ResponseWrapperV2<List<PolicyGroupDto>> getApprovedPartnerIdsWithPolicyGroups() {
        ResponseWrapperV2<List<PolicyGroupDto>> responseWrapper = new ResponseWrapperV2<>();
        try {
            String userId = getUserId();
            List<Partner> partnerList = partnerRepository.findByUserId(userId);
            if (!partnerList.isEmpty()) {
                List<PolicyGroupDto> policyGroupDtoList = new ArrayList<>();
                for (Partner partner : partnerList) {
                    String partnerType = partner.getPartnerTypeCode();
                    // Ignore, If the partner is a DEVICE or FTM partnertype
                    if (!skipDeviceOrFtmPartner(partner)
                            && partner.getApprovalStatus().equalsIgnoreCase(APPROVED)) {
                        PolicyGroupDto policyGroupDto = new PolicyGroupDto();
                        validatePartnerId(partner, userId);
                        validatePolicyGroupId(partner, userId);
                        PolicyGroup policyGroup = validatePolicyGroup(partner);
                        policyGroupDto.setPartnerId(partner.getId());
                        policyGroupDto.setPartnerType(partner.getPartnerTypeCode());
                        policyGroupDto.setPolicyGroupId(partner.getPolicyGroupId());
                        policyGroupDto.setPolicyGroupName(policyGroup.getName());
                        policyGroupDto.setPolicyGroupDescription(policyGroup.getDesc());
                        policyGroupDtoList.add(policyGroupDto);
                    }
                }
                responseWrapper.setResponse(policyGroupDtoList);
            } else {
                LOGGER.info("sessionId", "idType", "id", "User id does not exists.");
                throw new PartnerServiceException(ErrorCode.USER_ID_NOT_EXISTS.getErrorCode(),
                        ErrorCode.USER_ID_NOT_EXISTS.getErrorMessage());
            }
        } catch (PartnerServiceException ex) {
            LOGGER.info("sessionId", "idType", "id", "In getApprovedPartnerIdsWithPolicyGroups method of MultiPartnerServiceImpl - " + ex.getMessage());
            responseWrapper.setErrors(PartnerUtil.setErrorResponse(ex.getErrorCode(), ex.getErrorText()));
        } catch (Exception ex) {
            LOGGER.debug("sessionId", "idType", "id", ex.getStackTrace());
            LOGGER.error("sessionId", "idType", "id",
                    "In getApprovedPartnerIdsWithPolicyGroups method of MultiPartnerServiceImpl - " + ex.getMessage());
            String errorCode = ErrorCode.POLICY_GROUP_FETCH_ERROR.getErrorCode();
            String errorMessage = ErrorCode.POLICY_GROUP_FETCH_ERROR.getErrorMessage();
            responseWrapper.setErrors(PartnerUtil.setErrorResponse(errorCode, errorMessage));
        }
        responseWrapper.setId(getApprovedPartnerIdsWithPolicyGroupsId);
        responseWrapper.setVersion(VERSION);
        return responseWrapper;
    }

    @Override
    @Transactional
    public ResponseWrapperV2<List<ApprovedPolicyDto>> getAuthPartnersPolicies() {
        ResponseWrapperV2<List<ApprovedPolicyDto>> responseWrapper = new ResponseWrapperV2<>();
        try {
            String userId = getUserId();
            List<Partner> partnerList = partnerRepository.findByUserId(userId);
            if (!partnerList.isEmpty()) {
                List<ApprovedPolicyDto> approvedPolicyList = new ArrayList<>();
                for (Partner partner : partnerList) {
                    if (checkIfPartnerIsApprovedAuthPartner(partner)) {
                        validatePartnerId(partner, userId);
                        validatePolicyGroupId(partner, userId);
                        PolicyGroup policyGroup = validatePolicyGroup(partner);
                        ApprovedPolicyDto approvedPolicyDto = new ApprovedPolicyDto();
                        approvedPolicyDto.setPartnerId(partner.getId());
                        approvedPolicyDto.setPolicyGroupId(policyGroup.getId());
                        approvedPolicyDto.setPolicyGroupDescription(policyGroup.getDesc());
                        approvedPolicyDto.setPolicyGroupName(policyGroup.getName());
                        List<PartnerPolicyRequest> partnerPolicyRequestList = partner.getPartnerPolicyRequests();
                        List<ActivePolicyDto> activePolicyDtoList = new ArrayList<>();
                        if (!partnerPolicyRequestList.isEmpty()) {
                            for (PartnerPolicyRequest partnerPolicyRequest : partnerPolicyRequestList) {
                                if (partnerPolicyRequest.getStatusCode().equals(APPROVED)) {
                                    AuthPolicy policyDetails = authPolicyRepository.findActivePoliciesByPolicyGroupId(partner.getPolicyGroupId(), partnerPolicyRequest.getPolicyId());
                                    if (Objects.nonNull(policyDetails)) {
                                        ActivePolicyDto activePolicyDto = new ActivePolicyDto();
                                        activePolicyDto.setPolicyId(policyDetails.getId());
                                        activePolicyDto.setPolicyDescription(policyDetails.getDescr());
                                        activePolicyDto.setPolicyName(policyDetails.getName());
                                        activePolicyDtoList.add(activePolicyDto);
                                    } else {
                                        LOGGER.info("No matching policy not found for policy group ID :" + partner.getPolicyGroupId() + "and Policy ID :" + partnerPolicyRequest.getPolicyId());
                                        throw new PartnerServiceException(ErrorCode.MATCHING_POLICY_NOT_FOUND.getErrorCode(),
                                                ErrorCode.MATCHING_POLICY_NOT_FOUND.getErrorMessage());
                                    }
                                }
                            }
                            approvedPolicyDto.setActivePolicies(activePolicyDtoList);
                            approvedPolicyList.add(approvedPolicyDto);
                        } else {
                            approvedPolicyDto.setActivePolicies(activePolicyDtoList);
                            approvedPolicyList.add(approvedPolicyDto);
                        }
                    }
                }
                responseWrapper.setResponse(approvedPolicyList);
            } else {
                LOGGER.info("sessionId", "idType", "id", "User id does not exists.");
                throw new PartnerServiceException(ErrorCode.USER_ID_NOT_EXISTS.getErrorCode(),
                        ErrorCode.USER_ID_NOT_EXISTS.getErrorMessage());
            }
        } catch (PartnerServiceException ex) {
            LOGGER.info("sessionId", "idType", "id", "In getAuthPartnersPolicies method of MultiPartnerServiceImpl - " + ex.getMessage());
            responseWrapper.setErrors(PartnerUtil.setErrorResponse(ex.getErrorCode(), ex.getErrorText()));
        } catch (Exception ex) {
            LOGGER.debug("sessionId", "idType", "id", ex.getStackTrace());
            LOGGER.error("sessionId", "idType", "id",
                    "In getAuthPartnersPolicies method of MultiPartnerServiceImpl - " + ex.getMessage());
            String errorCode = ErrorCode.PARTNER_POLICY_FETCH_ERROR.getErrorCode();
            String errorMessage = ErrorCode.PARTNER_POLICY_FETCH_ERROR.getErrorMessage();
            responseWrapper.setErrors(PartnerUtil.setErrorResponse(errorCode, errorMessage));
        }
        responseWrapper.setId(getAuthPartnersPoliciesId);
        responseWrapper.setVersion(VERSION);
        return responseWrapper;
    }

    public static boolean checkIfPartnerIsApprovedAuthPartner(Partner partner) {
        String partnerType = partner.getPartnerTypeCode();
        String approvalStatus = partner.getApprovalStatus();
        if (Objects.isNull(partnerType) || partnerType.equals(BLANK_STRING)) {
            LOGGER.info("Partner Type is null or empty for partner id : " + partner.getId());
            throw new PartnerServiceException(ErrorCode.PARTNER_TYPE_NOT_EXISTS.getErrorCode(),
                    ErrorCode.PARTNER_TYPE_NOT_EXISTS.getErrorMessage());
        }
        if ((Objects.isNull(approvalStatus) || approvalStatus.equals(BLANK_STRING))) {
            LOGGER.info("Approval status is null or empty for partner id : " + partner.getId());
            throw new PartnerServiceException(ErrorCode.APPROVAL_STATUS_NOT_EXISTS.getErrorCode(),
                    ErrorCode.APPROVAL_STATUS_NOT_EXISTS.getErrorMessage());
        }
        return partnerType.equals(AUTH_PARTNER) && approvalStatus.equals(APPROVED);
    }

    public static void validatePartnerId(Partner partner, String userId) {
        if (Objects.isNull(partner.getId()) || partner.getId().equals(BLANK_STRING)) {
            LOGGER.info("Partner Id is null or empty for user id : " + userId);
            throw new PartnerServiceException(ErrorCode.PARTNER_ID_NOT_EXISTS.getErrorCode(),
                    ErrorCode.PARTNER_ID_NOT_EXISTS.getErrorMessage());
        }
    }

    public static void validatePolicyGroupId(Partner partner, String userId) {
        if (Objects.isNull(partner.getPolicyGroupId()) || partner.getPolicyGroupId().equals(BLANK_STRING)) {
            LOGGER.info("Policy group Id is null or empty for user id : " + userId);
            throw new PartnerServiceException(ErrorCode.POLICY_GROUP_ID_NOT_EXISTS.getErrorCode(),
                    ErrorCode.POLICY_GROUP_ID_NOT_EXISTS.getErrorMessage());
        }
    }

    public static boolean skipDeviceOrFtmPartner(Partner partner) {
        String partnerType = partner.getPartnerTypeCode();
        if (Objects.isNull(partnerType) || partnerType.equals(BLANK_STRING)) {
            LOGGER.info("Partner Type is null or empty for partner id : " + partner.getId());
            throw new PartnerServiceException(ErrorCode.PARTNER_TYPE_NOT_EXISTS.getErrorCode(),
                    ErrorCode.PARTNER_TYPE_NOT_EXISTS.getErrorMessage());
        }
        return partnerType.equals(DEVICE_PROVIDER) || partnerType.equals(FTM_PROVIDER);
    }

    private PolicyGroup validatePolicyGroup(Partner partner) throws PartnerServiceException {
        PolicyGroup policyGroup = policyGroupRepository.findPolicyGroupById(partner.getPolicyGroupId());
        if (Objects.isNull(policyGroup) || Objects.isNull(policyGroup.getName()) || policyGroup.getName().isEmpty()) {
            LOGGER.info("Policy Group is null or empty for partner id : {}", partner.getId());
            throw new PartnerServiceException(ErrorCode.POLICY_GROUP_NOT_EXISTS.getErrorCode(), ErrorCode.POLICY_GROUP_NOT_EXISTS.getErrorMessage());
        }
        return policyGroup;
    }

    @Override
    public ResponseWrapperV2<List<ApiKeyResponseDto>> getApiKeysForAuthPartners() {
        ResponseWrapperV2<List<ApiKeyResponseDto>> responseWrapper = new ResponseWrapperV2<>();
        try {
            String userId = getUserId();
            List<Partner> partnerList = partnerRepository.findByUserId(userId);
            if (!partnerList.isEmpty()) {
                List<ApiKeyResponseDto> apiKeyResponseDtoList = new ArrayList<>();
                for (Partner partner : partnerList) {
                    if (checkIfPartnerIsApprovedAuthPartner(partner)) {
                        validatePartnerId(partner, userId);
                        validatePolicyGroupId(partner, userId);
                        List<PartnerPolicy> apiKeyRequestsList = partnerPolicyRepository.findAPIKeysByPartnerId(partner.getId());
                        if (!apiKeyRequestsList.isEmpty()) {
                            for (PartnerPolicy partnerPolicy : apiKeyRequestsList) {
                                Optional<AuthPolicy> authPolicy = authPolicyRepository.findById(partnerPolicy.getPolicyId());
                                if (!authPolicy.isPresent()) {
                                    LOGGER.info("Policy does not exists.");
                                    throw new PartnerServiceException(ErrorCode.POLICY_NOT_EXIST.getErrorCode(),
                                            ErrorCode.POLICY_NOT_EXIST.getErrorMessage());
                                }
                                PolicyGroup policyGroup = authPolicy.get().getPolicyGroup();
                                if (Objects.isNull(policyGroup)) {
                                    LOGGER.info("Policy Group is null or empty");
                                    throw new PartnerServiceException(ErrorCode.POLICY_GROUP_NOT_EXISTS.getErrorCode(),
                                            ErrorCode.POLICY_GROUP_NOT_EXISTS.getErrorMessage());
                                }
                                ApiKeyResponseDto apiKeyResponseDto = new ApiKeyResponseDto();
                                apiKeyResponseDto.setApiKeyLabel(partnerPolicy.getLabel());
                                if (partnerPolicy.getIsActive()) {
                                    apiKeyResponseDto.setStatus(ACTIVE);
                                } else {
                                    apiKeyResponseDto.setStatus(INACTIVE);
                                }
                                apiKeyResponseDto.setPartnerId(partner.getId());
                                apiKeyResponseDto.setPolicyGroupId(policyGroup.getId());
                                apiKeyResponseDto.setPolicyGroupName(policyGroup.getName());
                                apiKeyResponseDto.setPolicyGroupDescription(policyGroup.getDesc());
                                apiKeyResponseDto.setPolicyId(authPolicy.get().getId());
                                apiKeyResponseDto.setPolicyName(authPolicy.get().getName());
                                apiKeyResponseDto.setPolicyDescription(authPolicy.get().getDescr());
                                apiKeyResponseDto.setCreatedDateTime(partnerPolicy.getCrDtimes());
                                apiKeyResponseDtoList.add(apiKeyResponseDto);
                            }
                        }
                    }
                }
                responseWrapper.setResponse(apiKeyResponseDtoList);
            } else {
                LOGGER.info("sessionId", "idType", "id", "User id does not exists.");
                throw new PartnerServiceException(ErrorCode.USER_ID_NOT_EXISTS.getErrorCode(),
                        ErrorCode.USER_ID_NOT_EXISTS.getErrorMessage());
            }
        } catch (PartnerServiceException ex) {
            LOGGER.info("sessionId", "idType", "id", "In getApiKeysForAuthPartners method of MultiPartnerServiceImpl - " + ex.getMessage());
            responseWrapper.setErrors(PartnerUtil.setErrorResponse(ex.getErrorCode(), ex.getErrorText()));
        } catch (Exception ex) {
            LOGGER.debug("sessionId", "idType", "id", ex.getStackTrace());
            LOGGER.error("sessionId", "idType", "id",
                    "In getApiKeysForAuthPartners method of MultiPartnerServiceImpl - " + ex.getMessage());
            String errorCode = ErrorCode.API_KEY_REQUESTS_FETCH_ERROR.getErrorCode();
            String errorMessage = ErrorCode.API_KEY_REQUESTS_FETCH_ERROR.getErrorMessage();
            responseWrapper.setErrors(PartnerUtil.setErrorResponse(errorCode, errorMessage));
        }
        responseWrapper.setId(getApiKeysForAuthPartnersId);
        responseWrapper.setVersion(VERSION);
        return responseWrapper;
    }

    private AuthUserDetails authUserDetails() {
        return (AuthUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private String getUserId() {
        String userId = authUserDetails().getUserId();
        return userId;
    }

    public static String getCertificateName(String subjectDN) {
        String[] parts = subjectDN.split(",");
        for (String part : parts) {
            if (part.trim().startsWith("CN=")) {
                return part.trim().substring(3);
            }
        }
        return BLANK_STRING;
    }
}
