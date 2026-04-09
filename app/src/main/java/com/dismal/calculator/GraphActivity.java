package com.dismal.calculator;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import com.dismal.calculator.text.GraphExpressionFormatter;
import com.dismal.calculator.view.GraphView;
import com.dismal.calculator.math.GraphModule;
import org.javia.arity.Symbols;
import java.util.List;

public class GraphActivity extends Activity {

    public static final String EXTRA_EXPR_NORMALIZED = "extra_expr_normalized";
    public static final String EXTRA_DEGREE_MODE = "extra_degree_mode";

    private GraphController mGraphController;
    private GraphAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph);

        final String expr = getIntent().getStringExtra(EXTRA_EXPR_NORMALIZED);
        final boolean isDegreeMode = getIntent().getBooleanExtra(EXTRA_DEGREE_MODE, false);

        final GraphView graphView = (GraphView) findViewById(R.id.graph_view);
        final ListView listView = (ListView) findViewById(R.id.graph_list);

        mGraphController = new GraphController(new GraphModule(new Symbols()), graphView);
        mGraphController.setIsDegreeMode(isDegreeMode);

        mAdapter = new GraphAdapter(this, graphView.getGraphs());
        listView.setAdapter(mAdapter);

        if (!TextUtils.isEmpty(expr)) {
            mGraphController.addNewGraph(expr);
            mAdapter.notifyDataSetChanged();
        }

        findViewById(R.id.btn_add_equation).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddGraphDialog();
            }
        });

        findViewById(R.id.btn_clear_equations).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mGraphController.clearGraphs();
                mAdapter.notifyDataSetChanged();
            }
        });
    }

    private void showAddGraphDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Equation");
        final EditText input = new EditText(this);
        input.setHint("e.g. y=x^2");
        builder.setView(input);
        builder.setPositiveButton("Add", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String formula = input.getText().toString();
                if (!TextUtils.isEmpty(formula)) {
                    mGraphController.addNewGraph(formula);
                    mAdapter.notifyDataSetChanged();
                }
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private class GraphAdapter extends ArrayAdapter<GraphView.Graph> {
        public GraphAdapter(Context context, List<GraphView.Graph> graphs) {
            super(context, 0, graphs);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final GraphView.Graph graph = getItem(position);
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.graph_entry, parent, false);
            }

            View colorIndicator = convertView.findViewById(R.id.graph_color_indicator);
            TextView formulaText = (TextView) convertView.findViewById(R.id.graph_expression_text);
            ImageButton removeButton = (ImageButton) convertView.findViewById(R.id.graph_remove_button);

            colorIndicator.setBackgroundColor(graph.getColor());
            formulaText.setText(GraphExpressionFormatter.format(getContext(), graph.getFormula()));
            
            final int[] palette = new int[] {
                0xFFF44336, 0xFFE91E63, 0xFF9C27B0, 0xFF673AB7,
                0xFF3F51B5, 0xFF2196F3, 0xFF03A9F4, 0xFF00BCD4,
                0xFF009688, 0xFF4CAF50, 0xFF8BC34A, 0xFFCDDC39,
                0xFFFFEB3B, 0xFFFFC107, 0xFFFF9800, 0xFFFF5722,
                0xFF795548, 0xFF9E9E9E, 0xFF607D8B, 0xFF000000
            };
            
            colorIndicator.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setTitle("Choose Color");
                    
                    String[] colorNames = new String[palette.length];
                    for (int i=0; i<palette.length; i++) {
                        colorNames[i] = "Color " + (i+1);
                    }
                    
                    builder.setItems(colorNames, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            graph.color = palette[which];
                            notifyDataSetChanged();
                            mGraphController.redraw();
                        }
                    });
                    
                    builder.show();
                }
            });

            removeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mGraphController.removeGraph(graph);
                    notifyDataSetChanged();
                }
            });

            return convertView;
        }
    }

    @Override
    protected void onDestroy() {
        if (mGraphController != null) {
            mGraphController.destroy();
        }
        super.onDestroy();
    }
}
