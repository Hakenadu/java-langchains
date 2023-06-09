package com.github.hakenadu.javalangchains.usecases;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import org.apache.logging.log4j.LogManager;
import org.apache.lucene.store.Directory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.hakenadu.javalangchains.chains.Chain;
import com.github.hakenadu.javalangchains.chains.data.reader.ReadDocumentsFromPdfChain;
import com.github.hakenadu.javalangchains.chains.data.retrieval.LuceneRetrievalChain;
import com.github.hakenadu.javalangchains.chains.data.writer.WriteDocumentsToLuceneDirectoryChain;
import com.github.hakenadu.javalangchains.chains.llm.openai.chat.OpenAiChatCompletionsChain;
import com.github.hakenadu.javalangchains.chains.llm.openai.chat.OpenAiChatCompletionsParameters;
import com.github.hakenadu.javalangchains.chains.qa.AnswerWithSources;
import com.github.hakenadu.javalangchains.chains.qa.CombineDocumentsChain;
import com.github.hakenadu.javalangchains.chains.qa.MapAnswerWithSourcesChain;
import com.github.hakenadu.javalangchains.chains.qa.ModifyDocumentsContentChain;
import com.github.hakenadu.javalangchains.chains.qa.split.JtokkitTextSplitter;
import com.github.hakenadu.javalangchains.chains.qa.split.SplitDocumentsChain;
import com.github.hakenadu.javalangchains.util.PromptTemplates;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.EncodingType;

/**
 * tests for a complete qa {@link Chain}
 * 
 * we'll read documents from our demo john doe pdfs at src/test/resources/pdf
 * and then ask questions about the protagonist.
 */
class RetrievalQaTest {

	private static Path tempIndexPath;
	private static Directory directory;

	@BeforeAll
	static void beforeAll() throws IOException, URISyntaxException {
		tempIndexPath = Files.createTempDirectory("lucene");

		/*
		 * We are also using a chain to create the lucene index directory
		 */
		final Chain<Path, Directory> createLuceneIndexChain = new ReadDocumentsFromPdfChain()
				// Optional Chain: split pdfs based on a max token count of 1000
				.chain(new SplitDocumentsChain(new JtokkitTextSplitter(
						Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE), 1000)))
				// Mandatory Chain: write split pdf documents to a lucene directory
				.chain(new WriteDocumentsToLuceneDirectoryChain(tempIndexPath));

		final Path pdfDirectoryPath = Paths.get(RetrievalQaTest.class.getResource("/pdf/qa").toURI());

		directory = createLuceneIndexChain.run(pdfDirectoryPath);
	}

	@AfterAll
	static void afterAll() throws IOException {
		directory.close();
		Files.walk(tempIndexPath).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
	}

	@Test
	void testQa() throws IOException {
		final OpenAiChatCompletionsParameters openAiChatParameters = new OpenAiChatCompletionsParameters()
				.temperature(0D).model("gpt-3.5-turbo");

		/*
		 * Chain 1: The retrievalChain is used to retrieve relevant documents from an
		 * index by using bm25 similarity
		 */
		try (final LuceneRetrievalChain retrievalChain = new LuceneRetrievalChain(directory, 1)) {

			/*
			 * Chain 2: The summarizeDocumentsChain is used to summarize documents to only
			 * contain the most relevant information. This is achieved using an OpenAI LLM
			 * (gpt-3.5-turbo in this case)
			 */
			final ModifyDocumentsContentChain summarizeDocumentsChain = new ModifyDocumentsContentChain(
					new OpenAiChatCompletionsChain(PromptTemplates.QA_SUMMARIZE, openAiChatParameters,
							System.getenv("OPENAI_API_KEY")));

			/*
			 * Chain 3: The combineDocumentsChain is used to combine the retrieved documents
			 * in a single prompt
			 */
			final CombineDocumentsChain combineDocumentsChain = new CombineDocumentsChain();

			/*
			 * Chain 4: The openAiChatChain is used to process the combined prompt using an
			 * OpenAI LLM (gpt-3.5-turbo in this case)
			 */
			final OpenAiChatCompletionsChain openAiChatChain = new OpenAiChatCompletionsChain(
					PromptTemplates.QA_COMBINE, openAiChatParameters, System.getenv("OPENAI_API_KEY"));

			/*
			 * Chain 5: The mapAnswerWithSourcesChain is used to map the llm string output
			 * to a complex object using a regular expression which splits the sources and
			 * the answer.
			 */
			final MapAnswerWithSourcesChain mapAnswerWithSourcesChain = new MapAnswerWithSourcesChain();

			// @formatter:off
			// we combine all chain links into a self contained QA chain
			final Chain<String, AnswerWithSources> qaChain = retrievalChain
					.chain(summarizeDocumentsChain)
					.chain(combineDocumentsChain)
					.chain(openAiChatChain)
					.chain(mapAnswerWithSourcesChain);
			// @formatter:on

			// the QA chain can now be called with a question and delivers an answer
			final AnswerWithSources answerToValidQuestion = qaChain.run("who is john doe?");
			assertNotNull(answerToValidQuestion, "no answer provided");
			assertFalse(answerToValidQuestion.getSources().isEmpty(), "no sources");
			LogManager.getLogger().info("answer to valid question: {}", answerToValidQuestion);
		}
	}
}
