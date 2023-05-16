/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.planner.optimizer.rule.read;

import static org.opensearch.sql.planner.optimizer.pattern.Patterns.aggregate;
import static org.opensearch.sql.planner.optimizer.pattern.Patterns.filter;
import static org.opensearch.sql.planner.optimizer.pattern.Patterns.highlight;
import static org.opensearch.sql.planner.optimizer.pattern.Patterns.limit;
import static org.opensearch.sql.planner.optimizer.pattern.Patterns.nested;
import static org.opensearch.sql.planner.optimizer.pattern.Patterns.project;
import static org.opensearch.sql.planner.optimizer.pattern.Patterns.scanBuilder;
import static org.opensearch.sql.planner.optimizer.pattern.Patterns.sort;
import static org.opensearch.sql.planner.optimizer.rule.read.TableScanPushDown.TableScanPushDownBuilder.match;

import com.facebook.presto.matching.Capture;
import com.facebook.presto.matching.Captures;
import com.facebook.presto.matching.Pattern;
import com.facebook.presto.matching.pattern.CapturePattern;
import com.facebook.presto.matching.pattern.WithPattern;
import java.util.function.BiFunction;
import org.opensearch.sql.planner.logical.LogicalPlan;
import org.opensearch.sql.planner.optimizer.Rule;
import org.opensearch.sql.storage.read.TableScanBuilder;

/**
 * Rule template for all table scan push down rules. Because all push down optimization rules
 * have similar workflow in common, such as a pattern that match an operator on top of table scan
 * builder, and action that eliminates the original operator if pushed down, this class helps
 * remove redundant code and improve readability.
 *
 * @param <T> logical plan node type
 */
public class TableScanPushDown<T extends LogicalPlan> implements Rule<T> {

  /** Push down optimize rule for filtering condition. */
  public static final Rule<?> PUSH_DOWN_FILTER =
      match(
          filter(
              scanBuilder()))
      .apply((filter, scanBuilder) -> scanBuilder.pushDownFilter(filter));

  /** Push down optimize rule for aggregate operator. */
  public static final Rule<?> PUSH_DOWN_AGGREGATION =
      match(
          aggregate(
              scanBuilder()))
      .apply((agg, scanBuilder) -> scanBuilder.pushDownAggregation(agg));

  /** Push down optimize rule for sort operator. */
  public static final Rule<?> PUSH_DOWN_SORT =
      match(
          sort(
              scanBuilder()))
      .apply((sort, scanBuilder) -> scanBuilder.pushDownSort(sort));

  /** Push down optimize rule for limit operator. */
  public static final Rule<?> PUSH_DOWN_LIMIT =
      match(
          limit(
              scanBuilder()))
      .apply((limit, scanBuilder) -> scanBuilder.pushDownLimit(limit));

  public static final Rule<?> PUSH_DOWN_PROJECT =
      match(
          project(
              scanBuilder()))
      .apply((project, scanBuilder) -> scanBuilder.pushDownProject(project));

  public static final Rule<?> PUSH_DOWN_HIGHLIGHT =
      match(
          highlight(
              scanBuilder()))
          .apply((highlight, scanBuilder) -> scanBuilder.pushDownHighlight(highlight));

  public static final Rule<?> PUSH_DOWN_NESTED =
      match(
          nested(
              scanBuilder()))
          .apply((nested, scanBuilder) -> scanBuilder.pushDownNested(nested));

  /** Pattern that matches a plan node. */
  private final WithPattern<T> pattern;

  /** Capture table scan builder inside a plan node. */
  private final Capture<TableScanBuilder> capture;

  /** Push down function applied to the plan node and captured table scan builder. */
  private final BiFunction<T, TableScanBuilder, Boolean> pushDownFunction;


  @SuppressWarnings("unchecked")
  private TableScanPushDown(WithPattern<T> pattern,
                           BiFunction<T, TableScanBuilder, Boolean> pushDownFunction) {
    this.pattern = pattern;
    this.capture = ((CapturePattern<TableScanBuilder>) pattern.getPattern()).capture();
    this.pushDownFunction = pushDownFunction;
  }

  @Override
  public Pattern<T> pattern() {
    return pattern;
  }

  @Override
  public LogicalPlan apply(T plan, Captures captures) {
    TableScanBuilder scanBuilder = captures.get(capture);
    if (pushDownFunction.apply(plan, scanBuilder)) {
      return scanBuilder;
    }
    return plan;
  }

  /**
   * Custom builder class other than generated by Lombok to provide more readable code.
   */
  static class TableScanPushDownBuilder<T extends LogicalPlan> {

    private WithPattern<T> pattern;

    public static <T extends LogicalPlan>
        TableScanPushDownBuilder<T> match(Pattern<T> pattern) {
      TableScanPushDownBuilder<T> builder = new TableScanPushDownBuilder<>();
      builder.pattern = (WithPattern<T>) pattern;
      return builder;
    }

    public TableScanPushDown<T> apply(
        BiFunction<T, TableScanBuilder, Boolean> pushDownFunction) {
      return new TableScanPushDown<>(pattern, pushDownFunction);
    }
  }
}