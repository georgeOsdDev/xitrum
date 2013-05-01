package xitrum.handler

import scala.collection.mutable.{HashMap => MHashMap, Map => MMap}

import org.jboss.netty.channel.Channel
import org.jboss.netty.handler.codec.http.{HttpRequest, HttpResponse}

import xitrum.Action
import xitrum.request.{FileUploadParams, Params, PathInfo}
import xitrum.routing.Route

/**
 * Env is basically a map for sharing data between handlers. But for more
 * typesafe, fixed data variables are put inside.
 */
class HandlerEnv extends MHashMap[String, Any] {
  var channel:          Channel          = null

  var request:          HttpRequest      = null  // Set by Request2Env
  var response:         HttpResponse     = null  // Set before the response is sent to client

  // Set by UriParser
  var pathInfo:         PathInfo         = null
  var uriParams:        Params           = null

  // Set by BodyParser
  var bodyParams:       Params           = null
  var fileUploadParams: FileUploadParams = null  // The filename has been sanitized for insecure character

  // Set by Dispatcher
  var route:            Route            = null  // The matched route
  var pathParams:       Params           = null  // The above params are real from the request, this one is logical from the request URL

  /**
   * A merge of all text params (uriParams, bodyParams, pathParams), as contrast
   * to file upload (fileParams).
   *
   * A val not a def, for speed, so that the calculation is done only once.
   *
   * lazy, so that bodyParams can be changed by ValidatorCaller.
   * Because this is a val, once this is accessed, either of the 3 params should
   * not be changed, because the change will not be reflected. If you still want
   * to change the the 3 params, after changing them, please also change this
   * textParams.
   */
  lazy val textParams: Params = {
    val ret = MMap[String, Seq[String]]()

    // The order is important because we want the later to overwrite the former
    ret ++= uriParams
    ret ++= bodyParams
    ret ++= pathParams

    ret
  }
}
