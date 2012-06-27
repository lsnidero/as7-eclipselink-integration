package id.au.ringerc.as7.eclipselinkintegration.test;

import static org.junit.Assert.assertNotNull;
import id.au.ringerc.as7.eclipselinkintegration.JBossAS7ServerPlatform;
import id.au.ringerc.as7.eclipselinkintegration.JBossArchiveFactoryImpl;
import id.au.ringerc.as7.eclipselinkintegration.JBossLogger;
import id.au.ringerc.as7.eclipselinkintegration.VFSArchive;
import id.au.ringerc.as7.eclipselinkintegration.test.entities.DummyEntity;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.EntityType;

import org.eclipse.persistence.Version;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.formatter.Formatters;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;

/**
 * The base class for all the test variants performs exactly the same series
 * of tests for each variant. The variants control the environment and
 * configuration by deploying a different persistence.xml file or making
 * other changes.
 * 
 * @author Craig Ringer <ringerc@ringerc.id.au>
 *
 */
class TestBase {
	
	protected static boolean verbose = false;
	private static File persistenceXml;

	protected static WebArchive makeDeployment(String persistenceXmlName) throws IOException {
		persistenceXml = new File( "src/test/resources/META-INF/", persistenceXmlName);
		WebArchive jar = ShrinkWrap.create(WebArchive.class)
				.addAsResource(persistenceXml, "META-INF/persistence.xml")
				.addAsWebInfResource(new File("src/test/resources/META-INF/beans.xml"), "beans.xml")
				.addClasses(DBProvider.class, DummyEntity.class, DummyEJB.class, TestBase.class);
		return jar;
	}
	
	protected static void printArchive(WebArchive war) throws IOException {
		if (verbose) {
			printPersistenceXml(persistenceXml);
			System.err.println("\n---");
			war.writeTo(System.err, Formatters.VERBOSE);
			System.err.println("\n---\n");
		}
	}

	
	@Inject
	private DummyEJB dummyEJB;
	
	protected void checkEclipseVersion() {
		System.err.println("ECLIPSE System version is: " + Version.getVersion());
	}
	
	protected void ensureInjected() {
		assertNotNull(dummyEJB);
	}

	protected void isTransactional() {
		try {
			dummyEJB.failsIfNotTransactional();
		} catch (javax.persistence.TransactionRequiredException ex) {
			Assert.fail("Operation failed with " + ex);
		} catch (javax.ejb.EJBException ex) {
			Assert.fail("Operation failed with " + ex);
		}
	}

	protected void dynamicMetaModelWorks() {
		try {
			dummyEJB.dynamicMetaModelWorks();
		} catch (java.lang.IllegalArgumentException ex) {
			Assert.fail("Unable to resolve metamodel class " + ex);
		} catch (javax.ejb.EJBException ex) {
			Assert.fail("Unable to resolve metamodel class " + ex);
		}
	}
	
	protected void databaseAccessWorks() {
		try {
			dummyEJB.createDummy(1);
			DummyEntity e = dummyEJB.getDummy(1);
			dummyEJB.deleteDummy(e);
		} catch (java.lang.IllegalArgumentException ex) {
			Assert.fail("Basic entity access failed " + ex);
		} catch (javax.ejb.EJBException ex) { 
			Assert.fail("Basic entity access failed " + ex);
		}
	}
	
	private static void printPersistenceXml(File persistenceXml) throws IOException {
		System.err.println("\nEclipse build-side version: " + Version.getVersion());
		System.err.println("\nUsing persistence.xml: " + persistenceXml);
		
		Writer w = new OutputStreamWriter(System.err);
		Reader r = new FileReader(persistenceXml);
		char[] buf = new char[1024];
		int bytesRead = 0;
		while ( (bytesRead = r.read(buf)) > 0 ) {
			w.write(buf, 0, bytesRead);
		}
		
	}
	
	@Stateless
	public static class DummyEJB {

		@Inject
		private EntityManager em;
		
		@TransactionAttribute(TransactionAttributeType.REQUIRED)
		public void failsIfNotTransactional() {
			Query q;
			q = em.createNativeQuery("SAVEPOINT txtest");
			q.executeUpdate();
			q = em.createNativeQuery("ROLLBACK TO SAVEPOINT txtest");
			q.executeUpdate();
		}
		
		public void dynamicMetaModelWorks() {
			EntityType<DummyEntity> x = em.getMetamodel().entity(DummyEntity.class);
			Attribute<? super DummyEntity, ?> y = x.getAttribute("dummy");
			if (!String.class.equals(y.getJavaType())) {
				throw new AssertionError("Unexpected class");
			}
		}
		
		public DummyEntity createDummy(Integer id) {
			DummyEntity e = new DummyEntity(id);
			em.persist(e);
			return e;
		}
		
		public DummyEntity getDummy(Integer id) {
			return em.find(DummyEntity.class, id);
		}
		
		public void deleteDummy(DummyEntity e) {
			e = em.merge(e);
			em.remove(e);
		}
		
	}

}
