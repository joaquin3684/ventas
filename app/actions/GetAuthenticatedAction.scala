package actions

import javax.inject.Inject

import play.api.mvc._
import repositories.UsuarioRepository
import requests.GetUserRequest
import pdi.jwt.JwtSession

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

case class GetAuthenticatedAction @Inject()(val parser: BodyParsers.Default)(implicit val executionContext: ExecutionContext)
  extends ActionBuilder[GetUserRequest, AnyContent] with ActionTransformer[Request, GetUserRequest] {

  def transform[A](request: Request[A]) = Future.successful {

    val token = request.headers.get("My-Authorization").get.split("Bearer ") match {
      case Array(_, tok) => Some(tok)
      case _ => None
    }
    var session = JwtSession.deserialize(token.get)

    val path = request.path
    val pantalla = path.split("/")(1)

    val userId = session.get("user_id").get.as[String]
    val pantallas = session.get("permisos").get.as[Seq[String]]
    val obrasSociales = session.get("obrasSociales").get.as[Seq[String]]
    if (pantallas.contains(pantalla)) new GetUserRequest(obrasSociales, userId, request) else {

      val futureRuta = UsuarioRepository.getRuta(path, pantallas)
      val ruta = Await.result(futureRuta, Duration.Inf)
      if (ruta.isEmpty) throw new RuntimeException("no tiene permiso para esta ruta") else new GetUserRequest(obrasSociales, userId, request)
    }
  }
}
