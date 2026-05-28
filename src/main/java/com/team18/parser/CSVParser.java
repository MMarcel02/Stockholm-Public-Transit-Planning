package com.team18.parser;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.IOException;

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
				return null;
			}

			return this.values[index];
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
			return new Row(this.colNames, row);
		} catch (IOException ex) {
			System.err.println("IO Exception in CSV parser");
			return null;
		}
	}
}

