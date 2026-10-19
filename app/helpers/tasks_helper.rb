module TasksHelper
  def task_date_label(date, today)
    return unless date

    days = (date - today).to_i
    case days
    when 0 then "Today"
    when 1 then "Tomorrow"
    when -1 then "Yesterday"
    when 2..7 then "In #{days} days"
    when -7..-2 then "#{-days} days overdue"
    else date.strftime(date.year == today.year ? '%-d %b' : '%-d %b %Y')
    end
  end
end
