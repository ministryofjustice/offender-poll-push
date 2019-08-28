package gov.uk.justice.digital.offenderpollpush.injection

import com.amazonaws.auth.{AWS4Signer, DefaultAWSCredentialsProviderChain}
import com.google.inject.name.Named
import com.google.inject.{Inject, Provider}
import gov.uk.justice.digital.offenderpollpush.helpers.AWSRequestSigningApacheInterceptor
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder
import org.elasticsearch.client.{RestClientBuilder, RestHighLevelClient}

class RestHighLevelClientProvider @Inject() (builder: RestClientBuilder,
                                            @Named("signSearchRequests") signSearchRequests: Boolean,
                                            @Named("searchAWSRegion") searchAWSRegion: String,
                                            @Named("searchAWSServiceName") searchAWSServiceName: String) extends Provider[RestHighLevelClient] {
  private val credentialsProvider = new DefaultAWSCredentialsProviderChain

  override def get: RestHighLevelClient = {
    if (signSearchRequests) {
      val signer = new AWS4Signer
      signer.setServiceName(searchAWSServiceName)
      signer.setRegionName(searchAWSRegion)

      new RestHighLevelClient(builder.setHttpClientConfigCallback((callback: HttpAsyncClientBuilder) => callback.addInterceptorLast(new AWSRequestSigningApacheInterceptor(searchAWSServiceName, signer, credentialsProvider))))
    } else {
      new RestHighLevelClient(builder)
    }
  }
}
