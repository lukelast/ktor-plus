package net.ghue.ktp.ktor.app.debug

// language=HTML
internal const val INDEX_TEMPLATE =
    """
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Debug Endpoints</title>
        <style>
            body {
                font-family: Arial, sans-serif;
                margin: 32px;
                background-color: #f4f4f4;
            }
            h1 {
                font-size: 32px;
                color: #222222;
                margin-bottom: 8px;
            }
            p {
                margin: 16px 0;
                color: #555555;
                font-size: 14px;
            }
            table {
                border-collapse: collapse;
                width: 100%;
                max-width: 1200px;
                margin: 16px 0;
                font-size: 14px;
                text-align: left;
                background-color: #ffffff;
                box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
                table-layout: fixed;
            }
            th, td {
                padding: 8px 16px;
                border-bottom: 1px solid #dddddd;
                word-break: break-word;
                overflow-wrap: anywhere;
            }
            th {
                background-color: #009879;
                color: #ffffff;
                text-align: left;
            }
            tr:hover {
                background-color: #f1f1f1;
            }
            .path-cell {
                width: 30%;
            }
            .description-cell {
                width: 50%;
            }
            .status-cell {
                width: 20%;
                text-align: center;
            }
            .status-enabled {
                display: inline-block;
                padding: 4px 12px;
                border-radius: 12px;
                background-color: #d4edda;
                color: #155724;
                font-weight: bold;
                font-size: 12px;
            }
            .status-disabled {
                display: inline-block;
                padding: 4px 12px;
                border-radius: 12px;
                background-color: #f8d7da;
                color: #721c24;
                font-weight: bold;
                font-size: 12px;
            }
            .enabled-row {
                background-color: #ffffff;
            }
            .disabled-row {
                background-color: #f9f9f9;
                opacity: 0.8;
            }
            .disabled-row .path-cell {
                color: #6c757d;
            }
            a {
                color: #007bff;
                text-decoration: none;
            }
            a:hover {
                text-decoration: underline;
            }
        </style>
    </head>
    <body>
        <h1>Debug Endpoints</h1>
        <p>Available diagnostic and debugging endpoints for this application.</p>
        <table>
            <thead>
                <tr>
                    <th class="path-cell">Endpoint</th>
                    <th class="description-cell">Description</th>
                    <th class="status-cell">Status</th>
                </tr>
            </thead>
            <tbody>
                {{ENDPOINT_ROWS}}
            </tbody>
        </table>
    </body>
    </html>
    """
