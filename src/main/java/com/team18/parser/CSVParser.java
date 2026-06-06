package com.team18.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class CSVParser {
	public String[] colNames;

	BufferedReader reader;

	public static class Row {
		public String[] names;
		public String[] values;

		public Row(String[] names, String row) {
			boolean inQuotes = false;
			boolean escaping = false;

			ArrayList<String> cols = new ArrayList<>();

			String current = "";

			for (int i = 0; i < row.length(); i++){
				char c = row.charAt(i);        

				if (escaping) {
					current += c;
					escaping = false;
					continue;
				}

				switch (c) {
					case '\"':
						inQuotes = !inQuotes;
						break;
					case '\\':
						escaping = true;
						break;
					case ',':
						if (inQuotes) current += c;
						else {
							cols.add(current);
							current = "";
						}
						break;
					default:
						current += c;
						break;
				}
			}

			cols.add(current);

			this.names = names;

			this.values = new String[cols.size()];
			this.values = cols.toArray(this.values);
		}

		public String getCol(String name) {
			int index = Arrays.asList(this.names).indexOf(name);
			if (index < 0) {
				return "";
			}

			return this.values[index];
		}

		public String getCol(String name, String defaultVal) {
			int index = Arrays.asList(this.names).indexOf(name);
			if (index < 0) {
				return defaultVal;
			}

			return this.values[index];
		}

		@Override
		public String toString() {
			return Arrays.toString(values);
		}
	}

	public CSVParser(BufferedReader reader) {
		this.reader = reader;

		String header = "";
		try {
			header = reader.readLine();
		} catch (IOException ex) {
			System.err.println("IO Exception in CSV parser");
		}

		Row headerRow = new Row(null, header);

		this.colNames = headerRow.values;
	}

	public Row nextRow() {
		try {
			String row = reader.readLine();
			if (row == null) return null;
			return new Row(this.colNames, row);
		} catch (IOException ex) {
			System.err.println("IO Exception in CSV parser");
			return null;
		}
	}

	public boolean hasAll(String... cols) {
		for (String col: cols) {
			int index = Arrays.asList(this.colNames).indexOf(col);
			if (index < 0) return false;
		}

		return true;
	}

	public boolean hasAny(String... cols) {
		for (String col: cols) {
			int index = Arrays.asList(this.colNames).indexOf(col);
			if (index >= 0) return true;
		}

		return false;
	}
}

