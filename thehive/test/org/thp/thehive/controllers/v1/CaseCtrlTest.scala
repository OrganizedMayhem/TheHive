package org.thp.thehive.controllers.v1

import java.util.Date

import scala.util.Try

import play.api.libs.json.{JsString, Json}
import play.api.test.{FakeRequest, NoMaterializer, PlaySpecification}

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v1.{InputCase, OutputCase, OutputCustomFieldValue}
import org.thp.thehive.models._

case class TestCase(
    title: String,
    description: String,
    severity: Int,
    startDate: Date,
    endDate: Option[Date] = None,
    tags: Set[String] = Set.empty,
    flag: Boolean,
    tlp: Int,
    pap: Int,
    status: String,
    summary: Option[String] = None,
    user: Option[String],
    customFields: Set[OutputCustomFieldValue] = Set.empty
)

object TestCase {

  def apply(outputCase: OutputCase): TestCase =
    TestCase(
      outputCase.title,
      outputCase.description,
      outputCase.severity,
      outputCase.startDate,
      outputCase.endDate,
      outputCase.tags,
      outputCase.flag,
      outputCase.tlp,
      outputCase.pap,
      outputCase.status,
      outputCase.summary,
      outputCase.user,
      outputCase.customFields
    )
}

class CaseCtrlTest extends PlaySpecification with Mockito {
  val dummyUserSrv               = DummyUserSrv(userId = "admin@thehive.local", permissions = Permissions.all)
  implicit val mat: Materializer = NoMaterializer

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val caseCtrl: CaseCtrl = app.instanceOf[CaseCtrl]

    s"[$name] case controller" should {

      "create a new case" in {
        val now = new Date()
        val request = FakeRequest("POST", "/api/v1/case")
          .withJsonBody(
            Json.toJson(
              InputCase(
                title = "case title (create case test)",
                description = "case description (create case test)",
                severity = Some(2),
                startDate = Some(now),
                tags = Set("tag1", "tag2"),
                flag = Some(false),
                tlp = Some(1),
                pap = Some(3)
              )
            )
          )
          .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> "default")
        val result = caseCtrl.create(request)
        status(result) must beEqualTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")
        val resultCase = contentAsJson(result).as[OutputCase]
        val expected = TestCase(
          title = "case title (create case test)",
          description = "case description (create case test)",
          severity = 2,
          startDate = now,
          endDate = None,
          tags = Set("tag1", "tag2"),
          flag = false,
          tlp = 1,
          pap = 3,
          status = "Open",
          summary = None,
          user = Some("user2@thehive.local"),
          customFields = Set.empty
        )

        TestCase(resultCase) must_=== expected
      }

      "create a new case using a template" in {
        val now = new Date()
        val request = FakeRequest("POST", "/api/v1/case")
          .withJsonBody(
            Json.toJsObject(
              InputCase(
                title = "case title (create case test with template)",
                description = "case description (create case test with template)",
                severity = None,
                startDate = Some(now),
                tags = Set("tag1", "tag2"),
                flag = Some(false),
                tlp = Some(1),
                pap = Some(3)
              )
            ) + ("caseTemplate" -> JsString("spam"))
          )
          .withHeaders("user" -> "user1@thehive.local")
        val result = caseCtrl.create(request)
        status(result) must_=== 201
        val resultCase = contentAsJson(result).as[OutputCase]
        val expected = TestCase(
          title = "[SPAM] case title (create case test with template)",
          description = "case description (create case test with template)",
          severity = 1,
          startDate = now,
          endDate = None,
          tags = Set("tag1", "tag2", "spam", "src:mail"),
          flag = false,
          tlp = 1,
          pap = 3,
          status = "Open",
          summary = None,
          user = Some("user1@thehive.local"),
          customFields = Set(
            OutputCustomFieldValue("boolean1", "boolean custom field", "boolean", None),
            OutputCustomFieldValue("string1", "string custom field", "string", Some("string1 custom field"))
          )
        )

        TestCase(resultCase) must_=== expected
      }

      "get a case" in {
        val request = FakeRequest("GET", s"/api/v1/case/#1")
          .withHeaders("user" -> "user1@thehive.local")
        val result     = caseCtrl.get("#1")(request)
        val resultCase = contentAsJson(result).as[OutputCase]
        val expected = TestCase(
          title = "case#1",
          description = "description of case #1",
          severity = 2,
          startDate = new Date(1531667370000L),
          endDate = None,
          tags = Set(),
          flag = false,
          tlp = 2,
          pap = 2,
          status = "Open",
          user = Some("user1@thehive.local")
        )

        TestCase(resultCase) must_=== expected
      }

      "update a case" in {
//        val updateRequest = FakeRequest("PATCH", s"/api/v1/case/#2")
//          .withJsonBody(
//            Json.obj(
//              "title"  → "new title",
//              "flag"   → false,
//              "tlp"    → 2,
//              "pap"    → 1,
//              "status" → "resolved"
//            ))
//          .withHeaders("user" → "user1@thehive.local")
//        val updateResult = caseCtrl.update("#2")(updateRequest)
//        status(updateResult) must_=== 204
//
//        val getRequest = FakeRequest("GET", s"/api/v1/case/#2")
//        val getResult  = caseCtrl.get("#2")(getRequest)
//        val resultCase = contentAsJson(getResult).as[OutputCase]
//        val expected = TestCase(
//          title = "new title",
//          description = "case description (update case test)",
//          severity = 2,
//          startDate = new Date(),
//          endDate = None,
//          tags = Set("tag1", "tag2"),
//          flag = false,
//          tlp = 2,
//          pap = 1,
//          status = "resolved",
//          user = Some(dummyUserSrv.authContext.userId)
//        )

//        TestCase(resultCase) must_=== expected
        pending
      }
    }
  }
}
