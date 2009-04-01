package gov.loc.repository.bagger.jdbc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.ParameterizedBeanPropertyRowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.jdbc.core.simple.SimpleJdbcTemplate;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.orm.ObjectRetrievalFailureException;
import gov.loc.repository.bagger.Bagger;
import gov.loc.repository.bagger.Contact;
import gov.loc.repository.bagger.Person;
import gov.loc.repository.bagger.Profile;
import gov.loc.repository.bagger.Project;
import gov.loc.repository.bagger.PersonProjects;
import gov.loc.repository.bagger.UserContact;
import gov.loc.repository.bagger.Organization;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A simple JDBC-based implementation of the {@link Profile} interface.
 *
 * <p>This class uses Java 5 language features and the {@link SimpleJdbcTemplate}
 * plus {@link SimpleJdbcInsert}. It also takes advantage of classes like
 * {@link BeanPropertySqlParameterSource} and
 * {@link ParameterizedBeanPropertyRowMapper} which provide automatic mapping
 * between JavaBean properties and JDBC parameters or query results.
 *
 * @author Ken Krebs
 */
@Service
@ManagedResource("bagger:type=Bagger")
public class JdbcBagger implements Bagger, JdbcBaggerMBean {

	private final Log logger = LogFactory.getLog(getClass());

	private SimpleJdbcTemplate simpleJdbcTemplate;

	private SimpleJdbcInsert insertPerson;
	private SimpleJdbcInsert insertProject;
	private SimpleJdbcInsert insertOrganization;
	private SimpleJdbcInsert insertContact;
	private SimpleJdbcInsert insertProfile;
	private SimpleJdbcInsert insertPersonProject;
	private SimpleJdbcInsert insertUserContact;

	private final List<Organization> orgs = new ArrayList<Organization>();

	protected ArrayList<String> commandList = new ArrayList<String>();
	String sqlCommand = "";

	@Autowired
	public void init(DataSource dataSource) {
		this.simpleJdbcTemplate = new SimpleJdbcTemplate(dataSource);

		this.insertPerson = new SimpleJdbcInsert(dataSource).withTableName("person").usingGeneratedKeyColumns("id");
		this.insertProject = new SimpleJdbcInsert(dataSource).withTableName("projects").usingGeneratedKeyColumns("id");
		this.insertOrganization = new SimpleJdbcInsert(dataSource).withTableName("organization").usingGeneratedKeyColumns("id");
		this.insertContact = new SimpleJdbcInsert(dataSource).withTableName("contact").usingGeneratedKeyColumns("id");
		this.insertProfile = new SimpleJdbcInsert(dataSource).withTableName("profile").usingGeneratedKeyColumns("id");
		this.insertPersonProject = new SimpleJdbcInsert(dataSource).withTableName("person_projects").usingGeneratedKeyColumns("id");
		this.insertUserContact = new SimpleJdbcInsert(dataSource).withTableName("user_contact").usingGeneratedKeyColumns("id");
	}


	@ManagedOperation
	@Transactional(readOnly = true)
	public void refreshCache() throws DataAccessException {
		synchronized (this.orgs) {
			logger.info("Refreshing vets cache");
		}
	}

	@Transactional(readOnly = true)
	public Collection<Organization> getOrganizations() throws DataAccessException {
		return this.simpleJdbcTemplate.query(
				"SELECT * FROM organization ORDER BY name",
				ParameterizedBeanPropertyRowMapper.newInstance(Organization.class));
	}

	@Transactional(readOnly = true)
	public Collection<Project> getProjects() throws DataAccessException {
		return this.simpleJdbcTemplate.query(
				"SELECT * FROM projects ORDER BY name",
				ParameterizedBeanPropertyRowMapper.newInstance(Project.class));
	}
	
	@Transactional(readOnly = true)
	public Collection<Organization> findOrganizations(String name) throws DataAccessException {
		List<Organization> orgs = this.simpleJdbcTemplate.query(
				"SELECT * FROM organization WHERE name like ?",
				ParameterizedBeanPropertyRowMapper.newInstance(Organization.class),
				name + "%");
		return orgs;
	}

	@Transactional(readOnly = true)
	public Collection<Profile> findProfiles(String name) throws DataAccessException {
		List<Profile> profiles = this.simpleJdbcTemplate.query(
				"SELECT * FROM profile WHERE status='A' AND username=?",
				ParameterizedBeanPropertyRowMapper.newInstance(Profile.class),
				name);
		for (int i=0; i < profiles.size(); i++) {
			int index = profiles.get(i).getId();
			Profile profile = loadProfile(index);
			profiles.set(i, profile);
		}
		return profiles;
	}

	@Transactional(readOnly = true)
	public Collection<Project> findProjects(int personId) throws DataAccessException {
		List<PersonProjects> personProjects = this.simpleJdbcTemplate.query(
				"SELECT * FROM person_projects WHERE person_id=?",
				ParameterizedBeanPropertyRowMapper.newInstance(PersonProjects.class),
				personId);
		ArrayList<Project> projectList = new ArrayList<Project>();
		for (int i=0; i < personProjects.size(); i++) {
			int projectId = personProjects.get(i).getProjectId();
			Project project = loadProject(projectId);
			projectList.add(project);
		}
		return projectList;
	}

	@Transactional(readOnly = true)
	public Profile loadProfile(int id) throws DataAccessException {
		Profile profile;
		try {
			profile = this.simpleJdbcTemplate.queryForObject(
					"SELECT * FROM profile WHERE id=?",
					ParameterizedBeanPropertyRowMapper.newInstance(Profile.class),
					id);
			Contact person = loadContact(profile.getProfilePersonId());
			profile.setPerson(person);
			Contact contact = loadContact(profile.getContactId());
			profile.setContact(contact);
			Project project = loadProject(profile.getProjectId());
			profile.setProject(project);
		}
		catch (EmptyResultDataAccessException ex) {
			throw new ObjectRetrievalFailureException(Profile.class, new Integer(id));
		}
		return profile;
	}

	@Transactional(readOnly = true)
	public Contact loadContact(int id) throws DataAccessException {
		Contact contact;
		try {
			contact = this.simpleJdbcTemplate.queryForObject(
					"SELECT * FROM contact WHERE id=?",
					ParameterizedBeanPropertyRowMapper.newInstance(Contact.class),
					id);
			Person person = loadPerson(contact.getPersonId());
			contact.setPerson(person);
			Organization organization = loadOrganization(contact.getOrganizationId());
			contact.setOrganization(organization);
		}
		catch (EmptyResultDataAccessException ex) {
			throw new ObjectRetrievalFailureException(Contact.class, new Integer(id));
		}
		return contact;
	}
	
	@Transactional(readOnly = true)
	public Person loadPerson(int id) throws DataAccessException {
		Person person;
		try {
			person = this.simpleJdbcTemplate.queryForObject(
					"SELECT * FROM person WHERE id=?",
					ParameterizedBeanPropertyRowMapper.newInstance(Person.class),
					id);
		}
		catch (EmptyResultDataAccessException ex) {
			throw new ObjectRetrievalFailureException(Person.class, new Integer(id));
		}
		return person;
	}

	@Transactional(readOnly = true)
	public Organization loadOrganization(int id) throws DataAccessException {
		Organization org;
		try {
			org = this.simpleJdbcTemplate.queryForObject(
					"SELECT * FROM organization WHERE id=?",
					ParameterizedBeanPropertyRowMapper.newInstance(Organization.class),
					id);
		}
		catch (EmptyResultDataAccessException ex) {
			throw new ObjectRetrievalFailureException(Organization.class, new Integer(id));
		}
		return org;
	}

	@Transactional(readOnly = true)
	public Project loadProject(int id) throws DataAccessException {
		Project project;
		try {
			project = this.simpleJdbcTemplate.queryForObject(
					"SELECT * FROM projects WHERE id=?",
					ParameterizedBeanPropertyRowMapper.newInstance(Project.class),
					id);
		}
		catch (EmptyResultDataAccessException ex) {
			throw new ObjectRetrievalFailureException(Project.class, new Integer(id));
		}
		return project;
	}
	
	@Transactional
	public void storePerson(Person person) throws DataAccessException {
		try {
			Person p = this.loadPerson(person.getId());
			person.setId(p.getId());
			this.simpleJdbcTemplate.update(
					"UPDATE person SET first_name=:firstName, middle_init=:middleInit, last_name=:lastName WHERE id=:id",
					new BeanPropertySqlParameterSource(person));
			sqlCommand = "UPDATE person SET first_name='" + person.getFirstName() + "', middle_init='" + person.getMiddleInit() + "', last_name='" + person.getLastName() + "' WHERE id=" + person.getId() + ";";
			commandList.add(sqlCommand);
		}
		catch (Exception ex) {
			try {
				Number newKey = this.insertPerson.executeAndReturnKey(new BeanPropertySqlParameterSource(person));
				person.setId(newKey.intValue());
				sqlCommand = "INSERT INTO person VALUES (" + newKey.intValue() + ", '" + person.getFirstName() + "', '" + person.getMiddleInit() + "', '" + person.getLastName() + "');";
				commandList.add(sqlCommand);
			}
			catch (Exception exception) {
				exception.printStackTrace();
				throw new UnsupportedOperationException("Profile update not supported");				
			}
		}
	}

	@Transactional
	public void storeContact(Contact contact) throws DataAccessException {
		Person person = contact.getPerson();
		Organization org = contact.getOrganization();
		try {
			Contact c = this.loadContact(contact.getId());
			contact.setId(c.getId());
			this.simpleJdbcTemplate.update(
					"UPDATE contact SET person_id=:personId, organization_id=:organizationId, email=:email, telephone=:telephone WHERE id=:id",
					new BeanPropertySqlParameterSource(contact));
			sqlCommand = "UPDATE contact SET person_id=" + person.getId() + ", organization_id=" + org.getId() + ", email='" + contact.getEmail() + "', telephone='" + contact.getTelephone() + "' WHERE id=" + contact.getId() + ";";
			commandList.add(sqlCommand);
		}
		catch (Exception ex) {
			try {
				Number newKey = this.insertContact.executeAndReturnKey(new BeanPropertySqlParameterSource(contact));
				contact.setId(newKey.intValue());
				sqlCommand = "INSERT INTO contact VALUES (" + newKey.intValue() + ", " + person.getId() + ", " + org.getId() + ", '" + contact.getEmail() + "', '" + contact.getTelephone() + "');";
				commandList.add(sqlCommand);
			}
			catch (Exception exception) {
				exception.printStackTrace();
				throw new UnsupportedOperationException("Profile update not supported");				
			}
		}
	}

	@Transactional
	public void storeOrganization(Organization org) throws DataAccessException {
		try {
			Organization organization = loadOrganization(org.getId());
			org.setId(organization.getId());
			this.simpleJdbcTemplate.update(
					"UPDATE organization SET name=:name, address=:address WHERE id=:id",
					new BeanPropertySqlParameterSource(org));
			sqlCommand = "UPDATE organization SET name='" + org.getName() + "', address='" + org.getAddress() + "' WHERE id=" + org.getId() + ";";
			commandList.add(sqlCommand);
		}
		catch (Exception ex) {
			try {
				Number newKey = this.insertOrganization.executeAndReturnKey(new BeanPropertySqlParameterSource(org));
				org.setId(newKey.intValue());
				sqlCommand = "INSERT INTO organization VALUES (" + newKey.intValue() + ", '" + org.getName() + "', '" + org.getAddress() + "');";
				commandList.add(sqlCommand);
			}
			catch (Exception exception) {
				exception.printStackTrace();
				throw new UnsupportedOperationException("Organization update not supported");				
			}
		}
	}


	@Transactional
	public void storeProfile(Profile profile) throws DataAccessException {
		Contact contact = profile.getContact();
		Contact user = profile.getPerson();
		Person userPerson = user.getPerson();
		try {
			Profile prof = loadProfile(profile.getId());
			profile.setId(prof.getId());
			this.simpleJdbcTemplate.update(
					"UPDATE profile SET username=:username, profile_person_id=:profilePersonId, project_id=:projectId, contact_id=:contactId WHERE id=:id",
					new BeanPropertySqlParameterSource(profile));
			sqlCommand = "UPDATE profile SET username='" + profile.getUsername() + "', profile_person_id=" + userPerson.getId() + ", project_id=" + profile.getProjectId() + ", contact_id=" + contact.getId() + " WHERE id=" + profile.getId() + ";";
			commandList.add(sqlCommand);
		}
		catch (Exception ex) {
			try {
				Number newKey = this.insertProfile.executeAndReturnKey(new BeanPropertySqlParameterSource(profile));
				profile.setId(newKey.intValue());
				sqlCommand = "INSERT INTO profile VALUES (" + newKey.intValue() + ", '" + profile.getUsername() + "', " + userPerson.getId() + ", " + profile.getProjectId() + ", " + contact.getId() + ", 'A', '2008-09-18');";
				commandList.add(sqlCommand);
			}
			catch (Exception exception) {
				exception.printStackTrace();
				throw new UnsupportedOperationException("Profile update not supported");				
			}
		}
	}
	
	public void storePersonProject(Person person, Project project) throws DataAccessException {
		try {
			PersonProjects personProject = new PersonProjects();
			personProject.setPersonId(person.getId());
			personProject.setProjectId(project.getId());
			Number newKey = this.insertPersonProject.executeAndReturnKey(new BeanPropertySqlParameterSource(personProject));
			personProject.setId(newKey.intValue());
			sqlCommand = "INSERT INTO person_projects VALUES (" + newKey.intValue() + ", " + person.getId() + ", " + project.getId() + ");";
			commandList.add(sqlCommand);
		}
		catch (Exception exception) {
			exception.printStackTrace();
		}
	}
	
	public void storeUserContact(String username, Contact user) throws DataAccessException {
		try {
			UserContact userContact = new UserContact();
			userContact.setUsername(username);
			userContact.setContactId(user.getId());
			Number newKey = this.insertUserContact.executeAndReturnKey(new BeanPropertySqlParameterSource(userContact));
			userContact.setId(newKey.intValue());
			sqlCommand = "INSERT INTO user_contact VALUES (" + newKey.intValue() + ", '" + username + "', " + user.getId() + ");";
			commandList.add(sqlCommand);
		}
		catch (Exception exception) {
			exception.printStackTrace();
		}
	}
/* */
	@Transactional
	public String storeBaggerUpdates(Collection<Profile> profiles, String homeDir) throws DataAccessException {
		String messages = null;
		try {
			Object[] profileList = profiles.toArray();
			for (int i=0; i < profileList.length; i++) {
				Profile profile = (Profile) profileList[i];
				Contact contact = profile.getContact();
				Person person = contact.getPerson();
				Organization org = contact.getOrganization();
				Contact user = profile.getPerson();
				Person userPerson = user.getPerson();
				Project project = profile.getProject();
				Organization userOrg = user.getOrganization();
				
				// TODO: Check if profile, contact, organization, etc. info exists
				// if it exists create the update sql string and save it.
				// If it doesn't exist create the insert sql string and save it.
				// Store the list of all sql strings to a file which will be
				// searched for, read, and executed on startup.
				if (person != null) this.storePerson(person);
				contact.setPerson(person);
				if (userPerson!= null) this.storePerson(userPerson);
				user.setPerson(userPerson);
				if (org != null) this.storeOrganization(org);
				contact.setOrganization(org);
				if (userOrg != null) this.storeOrganization(userOrg);
				user.setOrganization(userOrg);
				if (contact != null) this.storeContact(contact);
				profile.setContact(contact);
				profile.setContactId(contact.getId());
				if (user != null) this.storeContact(user);
				profile.setPerson(user);
				profile.setProfilePersonId(user.getId());
				profile.setProjectId(project.getId());
				if (profile != null) this.storeProfile(profile);
			}
			messages = write(homeDir);
		}
		catch (Exception ex) {
			messages = "JdbcBagger.storeBaggerUpdates exception: " + ex.getMessage();
			ex.printStackTrace();
		}
		return messages;
	}
/* */
/* */
	private String write(String homeDir) {
		String message = null;
		String name = "bagger.sql";
		try
		{
			File file = new File(homeDir, name);
			Writer writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
			logger.debug("JdbcBagger.write file: " + file);
			int listSize = this.commandList.size();
			for (int i=0; i < listSize; i++) {
				String s = this.commandList.get(i);
				logger.debug(s);
				writer.write(s+'\n');
			}
			writer.close();
		}
		catch(IOException e)
		{
			message = "JdbcBagger.write: " + e.getMessage();
			e.printStackTrace();
		}
		return message;		
	}
/* */	
	/**
	 * Creates a {@link MapSqlParameterSource} based on data values from the
	 * supplied {@link Profile} instance.
	 */
	private MapSqlParameterSource createProfileParameterSource(Profile profile) {
		return new MapSqlParameterSource()
			.addValue("id", profile.getId())
			.addValue("username", profile.getUsername())
			.addValue("person_id", profile.getProfilePersonId())
			.addValue("contact_id", profile.getContactId())
			.addValue("project_id", profile.getProjectId());
	}
}