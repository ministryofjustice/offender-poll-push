package gov.uk.justice.digital.offenderpollpush.helpers

import java.io.IOException
import java.net.{URI, URISyntaxException}
import java.util

import com.amazonaws.DefaultRequest
import com.amazonaws.auth.{AWSCredentialsProvider, Signer}
import com.amazonaws.http.HttpMethodName
import org.apache.http.{Header, HttpEntityEnclosingRequest, HttpException, HttpHost, HttpRequest, HttpRequestInterceptor, NameValuePair}
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.BasicHttpEntity
import org.apache.http.message.BasicHeader
import org.apache.http.protocol.HttpContext
import org.apache.http.protocol.HttpCoreContext.HTTP_TARGET_HOST


/**
 * An {@link HttpRequestInterceptor} that signs requests using any AWS {@link Signer}
 * and {@link AWSCredentialsProvider}.
 */
object AWSRequestSigningApacheInterceptor {
  /**
   *
   * @param params list of HTTP query params as NameValuePairs
   * @return a multimap of HTTP query params
   */
  private def nvpToMapParams(params: util.List[NameValuePair]) = {
    val parameterMap = new util.TreeMap[String, util.List[String]](String.CASE_INSENSITIVE_ORDER)
    import scala.collection.JavaConversions._
    for (nvp <- params) {
      val argsList = parameterMap.computeIfAbsent(nvp.getName, (k: String) => new util.ArrayList[String])
      argsList.add(nvp.getValue)
    }
    parameterMap
  }

  /**
   * @param headers modeled Header objects
   * @return a Map of header entries
   */
  private def headerArrayToMap(headers: Array[Header]) = {
    val headersMap = new util.TreeMap[String, String](String.CASE_INSENSITIVE_ORDER)
    for (header <- headers) {
      if (!skipHeader(header)) headersMap.put(header.getName, header.getValue)
    }
    headersMap
  }

  /**
   * @param header header line to check
   * @return true if the given header should be excluded when signing
   */
  private def skipHeader(header: Header) = "content-length".equalsIgnoreCase(header.getName) && "0" == header.getValue // Strip Content-Length: 0 || "host".equalsIgnoreCase(header.getName)// Host comes from endpoint

  /**
   * @param mapHeaders Map of header entries
   * @return modeled Header objects
   */
  private def mapToHeaderArray(mapHeaders: util.Map[String, String]) = {
    val headers = new Array[Header](mapHeaders.size)
    var i = 0
    import scala.collection.JavaConversions._
    for (headerEntry <- mapHeaders.entrySet) {
      headers({
        i += 1; i - 1
      }) = new BasicHeader(headerEntry.getKey, headerEntry.getValue)
    }
    headers
  }
}

class AWSRequestSigningApacheInterceptor(/**
                                          * The service that we're connecting to. Technically not necessary.
                                          * Could be used by a future Signer, though.
                                          */
                                         val service: String,

                                         /**
                                          * The particular signer implementation.
                                          */
                                         val signer: Signer,

                                         /**
                                          * The source of AWS credentials for signing.
                                          */
                                         val awsCredentialsProvider: AWSCredentialsProvider)

/**
 *
 * @param service                service that we're connecting to
 * @param signer                 particular signer implementation
 * @param awsCredentialsProvider source of AWS credentials for signing
 */
  extends HttpRequestInterceptor {
  /**
   * {@inheritDoc }
   */
  @throws[HttpException]
  @throws[IOException]
  override def process(request: HttpRequest, context: HttpContext): Unit = {
    val uriBuilder = new URIBuilder(request.getRequestLine.getUri)
    // Copy Apache HttpRequest to AWS DefaultRequest
    val signableRequest = new DefaultRequest[Any](service)
    val host = context.getAttribute(HTTP_TARGET_HOST).asInstanceOf[HttpHost]
    if (host != null) signableRequest.setEndpoint(URI.create(host.toURI))
    val httpMethod = HttpMethodName.fromValue(request.getRequestLine.getMethod)
    signableRequest.setHttpMethod(httpMethod)
    try signableRequest.setResourcePath(uriBuilder.build.getRawPath)
    catch {
      case e: URISyntaxException =>
        throw new IOException("Invalid URI", e)
    }
    if (request.isInstanceOf[HttpEntityEnclosingRequest]) {
      val httpEntityEnclosingRequest = request.asInstanceOf[HttpEntityEnclosingRequest]
      if (httpEntityEnclosingRequest.getEntity != null) signableRequest.setContent(httpEntityEnclosingRequest.getEntity.getContent)
    }
    signableRequest.setParameters(AWSRequestSigningApacheInterceptor.nvpToMapParams(uriBuilder.getQueryParams))
    signableRequest.setHeaders(AWSRequestSigningApacheInterceptor.headerArrayToMap(request.getAllHeaders))
    // Sign it
    signer.sign(signableRequest, awsCredentialsProvider.getCredentials)
    // Now copy everything back
    request.setHeaders(AWSRequestSigningApacheInterceptor.mapToHeaderArray(signableRequest.getHeaders))
    if (request.isInstanceOf[HttpEntityEnclosingRequest]) {
      val httpEntityEnclosingRequest = request.asInstanceOf[HttpEntityEnclosingRequest]
      if (httpEntityEnclosingRequest.getEntity != null) {
        val basicHttpEntity = new BasicHttpEntity
        basicHttpEntity.setContent(signableRequest.getContent)
        httpEntityEnclosingRequest.setEntity(basicHttpEntity)
      }
    }
  }
}