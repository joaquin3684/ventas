package controllers

import javax.inject.Inject

import actions.{AuthenticatedAction, GetAuthenticatedAction, JsonMapperAction}
import models._
import play.api.mvc.{AbstractController, ControllerComponents}
import repositories.UsuarioRepository
import services.JsonMapper
import com.github.t3hnar.bcrypt._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class UsuarioController @Inject()(cc: ControllerComponents, val userRepo: UsuarioRepository, val jsonMapper: JsonMapper, jsonMapperAction: JsonMapperAction, val authAction: AuthenticatedAction, val getAuthAction: GetAuthenticatedAction) extends AbstractController(cc){



  def create =  authAction { implicit request =>

    val rootNode = request.rootNode
    val perfilesJson = jsonMapper.getAndRemoveElement(rootNode, "perfiles")
    val obrasSocialesJson = jsonMapper.getAndRemoveElement(rootNode, "obrasSociales")
    val password = rootNode.get("password").asText().bcrypt
    jsonMapper.putElement(rootNode, "password", password)


    val userJson = rootNode.toString

    val obrasSociales = jsonMapper.fromJson[Seq[ObraSocial]](obrasSocialesJson)
    val perfiles = jsonMapper.fromJson[Seq[Perfil]](perfilesJson)

    val valiObrasSociales = obrasSociales.forall(x => request.obrasSociales.contains(x.nombre))

    if (valiObrasSociales) {
      val user = jsonMapper.fromJson[Usuario](userJson)
      val obsUser = obrasSociales.map { o => UsuarioObraSocial(user.user, o.nombre)}
      val perfUser = perfiles.map { p => UsuarioPerfil(user.user, p.nombre)}

      val futureUser = userRepo.create(user, obsUser, perfUser)
      Await.result(futureUser, Duration.Inf)

      Ok("guardado")
    } else throw new RuntimeException("obra social erronea")


  }


  def getById(user: String) = getAuthAction { implicit request =>
    implicit val obs: Seq[String] = request.obrasSociales
    val futureUser = userRepo.getById(user)
    val userWithRealtionships = Await.result(futureUser, Duration.Inf)
    Ok(mapToJsonString(userWithRealtionships))
  }


  def mapToJsonString(usuarioConRelaciones: Seq[(Usuario, ObraSocial, Perfil)]): String = {

    val user = usuarioConRelaciones.map(_._1).head
    val obrasSociales = usuarioConRelaciones.map(_._2.nombre).distinct
    val perfiles = usuarioConRelaciones.map(_._3.nombre).distinct

    val userJson = jsonMapper.toJson(user)
    val perfilesJson = jsonMapper.toJson(perfiles)
    val obsJson = jsonMapper.toJson(obrasSociales)

    val userNode = jsonMapper.getJsonNode(userJson)
    val perfilesNode = jsonMapper.getJsonNode(perfilesJson)
    val obsNode = jsonMapper.getJsonNode(obsJson)

    jsonMapper.addNode("perfiles", perfilesNode, userNode)
    jsonMapper.addNode("obrasSociales", obsNode, userNode)

    userNode.toString
  }


  def all() = getAuthAction { implicit request =>
    implicit val obs: Seq[String] = request.obrasSociales
    val futureUser = userRepo.all()
    val users = Await.result(futureUser, Duration.Inf)
    val u = users.distinct
    val json = jsonMapper.toJson(u)
    Ok(json)
  }


  def update(user: String) = authAction { implicit request =>

    implicit val obs: Seq[String] = request.obrasSociales
    val futureCheckObs = userRepo.checkObraSocial(user)
    val rootNode = request.rootNode
    val perfilesJson = jsonMapper.getAndRemoveElement(rootNode, "perfiles")
    val obrasSocialesJson = jsonMapper.getAndRemoveElement(rootNode, "obrasSociales")
    val userJson = rootNode.toString

    val obrasSociales = jsonMapper.fromJson[Seq[ObraSocial]](obrasSocialesJson)
    val perfiles = jsonMapper.fromJson[Seq[Perfil]](perfilesJson)

    val valiObrasSociales = obrasSociales.forall(x => request.obrasSociales.contains(x.nombre))
    if (valiObrasSociales) {

      val userModificado = jsonMapper.fromJson[Usuario](userJson)
      val obsUser = obrasSociales.map { o => UsuarioObraSocial(userModificado.user, o.nombre) }
      val perfUser = perfiles.map { p => UsuarioPerfil(userModificado.user, p.nombre) }

      val futureUser = userRepo.update(user, userModificado, perfUser, obsUser)
      val checkObs = Await.result(futureCheckObs, Duration.Inf)
      if(checkObs.nonEmpty) Await.result(futureUser, Duration.Inf) else throw new RuntimeException("obra social erronea")

    } else throw new RuntimeException("obra social erronea")


    Ok("modificado")

  }

  def delete(user: String) = getAuthAction { implicit request =>
    implicit val obs: Seq[String] = request.obrasSociales
    val futureCheckObs = userRepo.checkObraSocial(user)
    val check = Await.result(futureCheckObs, Duration.Inf)
    if(check.nonEmpty) {
      val futureUser = userRepo.delete(user)
      Await.result(futureUser, Duration.Inf)
      Ok("borrado")
    } else throw new RuntimeException("obra social erronea")

  }

  def cambiarPasswordPropia = authAction { implicit request =>
    val password = request.rootNode.get("password").asText().bcrypt
    val futureUser = userRepo.cambiarPassword(request.user, password)
    Await.result(futureUser, Duration.Inf)

    Ok("password modificada")

  }

  def cambiarPassword = authAction { implicit request =>
    implicit val obs: Seq[String] = request.obrasSociales
    val user = request.rootNode.get("user").asText()
    val futureCheckObs = userRepo.checkObraSocial(user)
    val password = request.rootNode.get("password").asText().bcrypt
    val check = Await.result(futureCheckObs, Duration.Inf)
    if(check.nonEmpty){
      val futureUser = userRepo.cambiarPassword(user, password)
      Await.result(futureUser, Duration.Inf)
      Ok("passowrd modificada")
    } else throw new RuntimeException("obra social erronea")


  }

  def perfiles = getAuthAction { implicit request =>
    val futurePerfiles = userRepo.getPerfiles
    val perf = Await.result(futurePerfiles, Duration.Inf)
    val perfiles = jsonMapper.toJson(perf)
    Ok(perfiles)
  }

}
