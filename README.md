# SheetPI
An example project for interacting with the Google Sheets API

The strings.xml file is missing due to private information being stored there. Strings that must be included for this project to work are:
    app_name
    option_one
    option_two

    click_prompt

    progress_msg

    weight_hint
    weight_error
    weight_button_label

    description_hint
    description_error
    amount_hint
    amount_error
    debt_button_label

    google_play_error
    empty_result_error
    cancelled
    exception_error
    network_error

    debt_sheet_id
    weight_sheet_id
    
This project is meant to append certain values to a spreadsheet list. The weight spreadsheet has column A for date and column B for weight.
By appending the current measurement on the bottom of that list, the chart in the spreadsheet is automatically updated.

Similarily for the debt sheet, column A is a description, column B the amount that the "Option One"-Person ows and column C the amount of debt of the "Option Two"-Person.
New values must also be added at the bottom. Range E5:D6 is a summary of total debt and who ows who that is printed out after the submission.
