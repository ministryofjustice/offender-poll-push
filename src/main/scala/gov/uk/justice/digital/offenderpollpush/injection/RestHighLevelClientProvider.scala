package gov.uk.justice.digital.offenderpollpush.injection

import com.google.inject.{Inject, Provider}
import org.elasticsearch.client.{RestClientBuilder, RestHighLevelClient}

class RestHighLevelClientProvider @Inject() (builder: RestClientBuilder) extends Provider[RestHighLevelClient] {

  override def get() = new RestHighLevelClient(builder)
}
