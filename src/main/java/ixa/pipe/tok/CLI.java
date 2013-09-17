/*
 *Copyright 2013 Rodrigo Agerri

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package ixa.pipe.tok;

import ixa.kaflib.KAFDocument;
import ixa.pipe.resources.Formats;
import ixa.pipe.resources.Resources;
import ixa.pipe.seg.SegmenterMoses;
import ixa.pipe.seg.SegmenterOpenNLP;
import ixa.pipe.seg.SentenceSegmenter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

/**
 * ixa-pipe tokenization
 * 
 * This module implements two sentence segmenters and tokenizers: 1. Rule-based
 * loosely inspired by the moses tokenizer
 * (https://github.com/moses-smt/mosesdecoder)
 * 
 * 2. Machine learning based with models trained using Apache OpenNLP API (on
 * CoNLL 2002 and 2003 corpora)
 * 
 * @author ragerri
 * @version 1.0
 * 
 */

public class CLI {

  /**
   * BufferedReader (from standard input) and BufferedWriter are opened. The
   * module takes plain text from standard input and produces tokenized text by
   * sentences. The tokens are then placed into the <wf> elements of KAF
   * document. The KAF document is passed via standard output.
   * 
   * @param args
   * @throws IOException
   */

  public static void main(String[] args) throws IOException {

    Namespace parsedArguments = null;

    // create Argument Parser
    ArgumentParser parser = ArgumentParsers
        .newArgumentParser("ixa-pipe-tok-1.0.jar")
        .description(
            "ixa-pipe-tok-1.0 is a multilingual Tokenizer module developed by IXA NLP Group.\n");

    // specify language
    parser
        .addArgument("-l", "--lang")
        .choices("en", "es")
        .required(true)
        .help(
            "It is REQUIRED to choose a language to perform annotation with IXA-Pipeline");

    // specify tokenization method

    parser
        .addArgument("-m", "--method")
        .choices("moses", "ml")
        .setDefault("moses")
        .help(
            "Tokenization method: Choose 'moses' for a (slightly modified and extended) re-implementation of the rule-based Moses MT system tokenizer (this is the default);"
                + " 'ml' for Apache OpenNLP trained probabilistic models. ");

    /*
     * Parse the command line arguments
     */

    // catch errors and print help
    try {
      parsedArguments = parser.parseArgs(args);
    } catch (ArgumentParserException e) {
      parser.handleError(e);
      System.out
          .println("Run java -jar ixa-pipe-tok/target/ixa-pipe-tok-1.0.jar -help for details");
      System.exit(1);
    }

    /*
     * Load language and tokenizer method parameters and construct annotators,
     * read and write kaf
     */

    String lang = parsedArguments.getString("lang");
    String method = parsedArguments.getString("method");

    Resources resourceRetriever = new Resources();
    Formats formatter = new Formats();
    Annotate annotator = new Annotate();
    BufferedReader breader = null;
    BufferedWriter bwriter = null;
    KAFDocument kaf = new KAFDocument(lang, "v1.opener");

    // choosing tokenizer and resources by language

    TokTokenizer tokenizer = null;
    SentenceSegmenter segmenter = null;
    if (method.equalsIgnoreCase("ml")) {
      segmenter = new SegmenterOpenNLP(lang);
      tokenizer = new TokenizerOpenNLP(lang);
    }

    else {
      InputStream nonBreaker = resourceRetriever.getNonBreakingPrefixes(lang);
      segmenter = new SegmenterMoses(nonBreaker);
      nonBreaker = resourceRetriever.getNonBreakingPrefixes(lang);
      tokenizer = new TokenizerMoses(nonBreaker, lang);
    }

    // reading standard input, segment and tokenize
    try {
      breader = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
      bwriter = new BufferedWriter(new OutputStreamWriter(System.out, "UTF-8"));

      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = breader.readLine()) != null) {
        line = formatter.cleanWeirdChars(line);
        sb.append(line).append("<JA>");
      }

      String text = sb.toString();
      // tokenize and create KAF
      annotator.annotateTokensToKAF(text, lang, segmenter, tokenizer, kaf);

      // write kaf document
      kaf.addLinguisticProcessor("text", "ixa-pipe-tok-" + lang, "1.0");
      bwriter.write(kaf.toString());
      bwriter.close();

    } catch (IOException e) {
      e.printStackTrace();
    }

  }
}
