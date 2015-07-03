package org.ednovo.gooru.domain.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ednovo.gooru.application.util.GooruImageUtil;
import org.ednovo.gooru.application.util.TaxonomyUtil;
import org.ednovo.gooru.core.api.model.ActionResponseDTO;
import org.ednovo.gooru.core.api.model.Collection;
import org.ednovo.gooru.core.api.model.CollectionType;
import org.ednovo.gooru.core.api.model.Identity;
import org.ednovo.gooru.core.api.model.InviteUser;
import org.ednovo.gooru.core.api.model.User;
import org.ednovo.gooru.core.api.model.UserClass;
import org.ednovo.gooru.core.api.model.UserGroupAssociation;
import org.ednovo.gooru.core.application.util.BaseUtil;
import org.ednovo.gooru.core.constant.ConfigConstants;
import org.ednovo.gooru.core.constant.ConstantProperties;
import org.ednovo.gooru.core.constant.ParameterProperties;
import org.ednovo.gooru.domain.service.setting.SettingService;
import org.ednovo.gooru.infrastructure.persistence.hibernate.ClassRepository;
import org.ednovo.gooru.infrastructure.persistence.hibernate.CollectionDao;
import org.ednovo.gooru.infrastructure.persistence.hibernate.InviteRepository;
import org.ednovo.gooru.infrastructure.persistence.hibernate.UserRepository;
import org.ednovo.gooru.infrastructure.persistence.hibernate.customTable.CustomTableRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

@Service
public class ClassServiceImpl extends BaseServiceImpl implements ClassService, ConstantProperties, ParameterProperties {

	@Autowired
	private ClassRepository classRepository;

	@Autowired
	private SettingService settingService;

	@Autowired
	private CollectionDao collectionDao;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private InviteRepository inviteRepository;

	@Autowired
	private CustomTableRepository customTableRepository;

	@Override
	@Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
	public ActionResponseDTO<UserClass> createClass(UserClass userClass, User user) {
		Errors errors = validateClass(userClass);
		if (!errors.hasErrors()) {
			userClass.setOrganization(user.getOrganization());
			userClass.setActiveFlag(true);
			userClass.setUserGroupType(USER);
			userClass.setPartyName(GOORU);
			userClass.setUserUid(user.getGooruUId());
			userClass.setPartyType(GROUP);
			userClass.setCreatedOn(new Date(System.currentTimeMillis()));
			userClass.setGroupCode(BaseUtil.generateBase48Encode(7));
			this.getClassRepository().save(userClass);
		}
		return new ActionResponseDTO<UserClass>(userClass, errors);
	}

	@Override
	@Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
	public void updateClass(String classUId, UserClass newUserClass, User user) {
		UserClass userClass = this.getClassRepository().getClassById(classUId);
		rejectIfNull(userClass, GL0056, CLASS);

		if (newUserClass.getName() != null) {
			userClass.setName(newUserClass.getName());
		}
		if (newUserClass.getDescription() != null) {
			userClass.setDescription(newUserClass.getDescription());
		}
		if (newUserClass.getVisibility() != null) {
			userClass.setVisibility(newUserClass.getVisibility());
		}
		if (newUserClass.getMinimumScore() != null) {
			userClass.setMinimumScore(newUserClass.getMinimumScore());
		}
		if (newUserClass.getGrades() != null) {
			userClass.setGrades(newUserClass.getGrades());
		}
		if (newUserClass.getCourseGooruOid() != null) {
			Collection collection = this.getCollectionDao().getCollectionByType(newUserClass.getCourseGooruOid(), CollectionType.COURSE.getCollectionType());
			rejectIfNull(collection, GL0056, COURSE);
			userClass.setCourseContentId(collection.getContentId());
		}
		userClass.setLastModifiedOn(new Date(System.currentTimeMillis()));
		userClass.setLastModifiedUserUid(user.getPartyUid());
		this.getClassRepository().save(userClass);

	}

	@Override
	@Transactional(readOnly = true, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
	public List<Map<String, Object>> getClasses(String gooruUid, int limit, int offset) {
		List<Map<String, Object>> resultSet = null;
		List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
		if (gooruUid != null) {
			resultSet = this.getClassRepository().getClasses(gooruUid, limit, offset);
		} else {
			resultSet = this.getClassRepository().getClasses(limit, offset);
		}
		if (resultSet != null) {
			for (Map<String, Object> result : resultSet) {
				results.add(setClass(result));
			}
		}
		return results;
	}

	@Override
	@Transactional(readOnly = true, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
	public List<Map<String, Object>> getStudyClasses(String gooruUid, int limit, int offset) {
		List<Map<String, Object>> resultSet = this.getClassRepository().getStudyClasses(gooruUid, limit, offset);
		List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
		if (resultSet != null) {
			for (Map<String, Object> result : resultSet) {
				results.add(setClass(result));
			}
		}
		return results;
	}

	@Override
	@Transactional(readOnly = true, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
	public List<Map<String, Object>> getClassesByCourse(String courseGooruOid, int limit, int offset) {
		List<Map<String, Object>> resultSet = this.getClassRepository().getClassesByCourse(courseGooruOid, limit, offset);
		List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
		if (resultSet != null) {
			for (Map<String, Object> result : resultSet) {
				results.add(setClass(result));
			}
		}
		return results;
	}

	@Override
	@Transactional(readOnly = true, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
	public List<Map<String, Object>> getMember(String classUid, int limit, int offset) {
		final List<Map<String, Object>> members = this.getClassRepository().getMember(classUid, limit, offset);
		final List<Map<String, Object>> userList = new ArrayList<Map<String, Object>>();
		for (Map<String, Object> user : members) {
			user.put(PROFILE_IMG_URL, BaseUtil.changeHttpsProtocolByHeader(settingService.getConfigSetting(ConfigConstants.PROFILE_IMAGE_URL, TaxonomyUtil.GOORU_ORG_UID)) + "/" + String.valueOf(user.get(GOORU_UID)) + ".png");
			userList.add(user);
		}
		return userList;
	}

	@Override
	@Transactional(readOnly = true, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
	public Map<String, Object> getClass(String classUid) {
		Map<String, Object> result = this.getClassRepository().getClass(classUid);
		rejectIfNull(result, GL0056, CLASS);
		setClass(result);
		return result;
	}

	private Map<String, Object> setClass(Map<String, Object> result) {
		result.put(USER, setUser(result.get(GOORU_UID), result.get(USER_NAME), result.get(GENDER)));
		Object thumbnail = result.get(THUMBNAIL);
		if (thumbnail != null) {
			result.put(THUMBNAILS, GooruImageUtil.getThumbnails(thumbnail));
		}
		return result;
	}

	private Map<String, Object> setUser(Object userUid, Object username, Object gender) {
		Map<String, Object> user = new HashMap<String, Object>();
		user.put(GOORU_UID, userUid);
		user.put(USER_NAME, username);
		user.put(GENDER, gender);
		return user;
	}

	private Errors validateClass(final UserClass userClass) {
		final Errors errors = new BindException(userClass, CLASS);
		rejectIfNullOrEmpty(errors, userClass.getName(), NAME, GL0006, generateErrorMessage(GL0006, NAME));
		return errors;
	}

	@Override
	@Transactional(readOnly = true, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
	public UserClass getClassById(String classUid) {
		return this.getClassRepository().getClassById(classUid);
	}

	@Override
	@Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
	public void deleteUserFromClass(final String classUid, final String userUid, User user) {
		UserClass userClass = this.getClassRepository().getClassById(classUid);
		rejectIfNull(userClass, GL0056, CLASS);
		if (userClass.getUserUid().equals(user.getGooruUId()) || user.getGooruUId().equals(userUid)) {
			this.getClassRepository().deleteUserFromClass(classUid, userUid);
		} else {
			throw new AccessDeniedException(generateErrorMessage(GL0089));
		}
	}

	@Override
	@Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
	public void deleteClass(String classUId, User user) {
		UserClass userClass = this.getClassRepository().getClassById(classUId);
		rejectIfNull(userClass, GL0056, 404, CLASS);
		if (userClass.getUserUid().equals(user.getGooruUId())) {
			this.getClassRepository().remove(userClass);
		} else {
			throw new AccessDeniedException(generateErrorMessage(GL0089));
		}
	}

	@Override
	@Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
	public void joinClass(String classUid, User user) {
		UserClass userClass = this.getClassRepository().getClassById(classUid);
		rejectIfNull(userClass, GL0056, 404, CLASS);
		Identity identity = this.getUserRepository().findUserByGooruId(user.getPartyUid());
		if (identity != null) {
			UserGroupAssociation userGroupAssociation = this.getUserRepository().getUserGroupMemebrByGroupUid(userClass.getPartyUid(), identity.getUser().getPartyUid());
			if (userGroupAssociation == null) {
				userGroupAssociation = new UserGroupAssociation(0, identity.getUser(), new Date(System.currentTimeMillis()), userClass);
				this.getUserRepository().save(userGroupAssociation);
				userClass.setLastModifiedOn(new Date(System.currentTimeMillis()));
				this.getClassRepository().save(userClass);
				InviteUser inviteUser = this.getInviteRepository().findInviteUserById(identity.getExternalId(), userClass.getPartyUid(), null);
				if (inviteUser != null) {
					inviteUser.setStatus(this.getCustomTableRepository().getCustomTableValue(INVITE_USER_STATUS, ACTIVE));
					inviteUser.setJoinedDate(new Date(System.currentTimeMillis()));
					this.getInviteRepository().save(inviteUser);
				}
			}
		}
	}

	public CollectionDao getCollectionDao() {
		return collectionDao;
	}

	public ClassRepository getClassRepository() {
		return classRepository;
	}

	public UserRepository getUserRepository() {
		return userRepository;
	}

	public InviteRepository getInviteRepository() {
		return inviteRepository;
	}

	public CustomTableRepository getCustomTableRepository() {
		return customTableRepository;
	}

}